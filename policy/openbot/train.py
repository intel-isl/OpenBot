from dataclasses import dataclass
import os
import re
import threading

import matplotlib.pyplot as plt
import numpy as np
import tensorflow as tf

from . import (
    associate_frames,
    callbacks,
    dataloader,
    dataset_dir,
    losses,
    metrics,
    models,
    models_dir,
    utils,
)

os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"
# 0 = all messages are logged (default behavior)
# 1 = INFO messages are not printed
# 2 = INFO and WARNING messages are not printed
# 3 = INFO, WARNING, and ERROR messages are not printed

# On Mac you may encounter an error related to OMP, this is a workaround, but slows down the code
# https://github.com/dmlc/xgboost/issues/1715
os.environ["KMP_DUPLICATE_LIB_OK"] = "True"

AUTOTUNE = tf.data.experimental.AUTOTUNE

dataset_name = "my_openbot"

load_from_tf_record = True
if (load_from_tf_record):
    train_data_dir = os.path.join(dataset_dir, "tfrecords/train.tfrec")
    test_data_dir = os.path.join(dataset_dir, "tfrecords/test.tfrec")
else:
    train_data_dir = os.path.join(dataset_dir, "train_data")
    test_data_dir = os.path.join(dataset_dir, "test_data")


@dataclass
class Hyperparameters:
    MODEL: str = "cil_mobile"

    TRAIN_BATCH_SIZE: int = 16
    TEST_BATCH_SIZE: int = 16
    LEARNING_RATE: float = 0.0001
    NUM_EPOCHS: int = 10

    BATCH_NORM: bool = True
    FLIP_AUG: bool = False
    CMD_AUG: bool = False

    USE_LAST: bool = False

    @classmethod
    def parse(cls, name):
        m = re.match(
            r".*_((cil|pilot)_.+)_lr(\d+.\d+)_bz(\d+)(_bn)?(_flip)?(_cmd)?", name
        )
        params = Hyperparameters(m[1], int(m[4]), int(m[4]), float(m[3]))
        params.BATCH_NORM = m[5] is not None
        params.FLIP_AUG = m[6] is not None
        params.CMD_AUG = m[7] is not None
        return params

    def __str__(self):
        model_name = "{0}_lr{1}_bz{2}".format(
            self.MODEL, self.LEARNING_RATE, self.TRAIN_BATCH_SIZE
        )

        if self.BATCH_NORM:
            model_name += "_bn"
        if self.FLIP_AUG:
            model_name += "_flip"
        if self.CMD_AUG:
            model_name += "_cmd"

        return model_name


@dataclass
class Training:
    def __init__(self, params: Hyperparameters):
        self.hyperparameters = params
        self.NETWORK_IMG_WIDTH = 0
        self.NETWORK_IMG_HEIGHT = 0
        self.train_datasets = []
        self.test_datasets = []
        self.image_count_train = 0
        self.image_count_test = 0
        self.train_ds = None
        self.test_ds = None
        self.history = None
        self.model_name = ""
        self.checkpoint_path = ""
        self.log_path = ""
        self.loss_fn = None
        self.metric_list = None
        self.custom_objects = None


class CancelledException(BaseException):
    pass


class MyCallback(tf.keras.callbacks.Callback):
    cancelled: threading.Event
    model: tf.keras.Model

    def __init__(self, broadcast, cancelled):
        super().__init__()
        self.cancelled = cancelled
        self.broadcast = broadcast

        self.epoch = 0
        self.step = 0

    def on_epoch_begin(self, epoch, logs=None):
        self.epoch = epoch

    def on_epoch_end(self, epoch, logs=None):
        msg = logs.copy()
        msg["epoch"] = epoch
        print("on_epoch_end", msg)
        self.broadcast("logs", msg)

    def on_batch_end(self, batch, logs=None):
        if self.cancelled.is_set():
            raise CancelledException
        self.step = batch + 1
        epochs = self.params["epochs"]
        steps = self.params["steps"]
        self.broadcast(
            "progress",
            dict(
                epoch=int(100 * self.step / steps),
                train=int(100 * (self.epoch * steps + self.step) / (epochs * steps)),
            ),
        )


def process_data(tr: Training):
    tr.train_datasets = utils.list_dirs(train_data_dir)
    tr.test_datasets = utils.list_dirs(test_data_dir)

    print(tr.hyperparameters)
    print("Train Datasets: ", len(tr.train_datasets))
    print("Test Datasets: ", len(tr.test_datasets))

    # 1ms
    max_offset = 1e3
    train_frames = associate_frames.match_frame_ctrl_cmd(
        train_data_dir,
        tr.train_datasets,
        max_offset,
        redo_matching=False,
        remove_zeros=True,
    )
    test_frames = associate_frames.match_frame_ctrl_cmd(
        test_data_dir,
        tr.test_datasets,
        max_offset,
        redo_matching=False,
        remove_zeros=True,
    )

    tr.image_count_train = len(train_frames)
    tr.image_count_test = len(test_frames)
    print(
        "There are %d train images and %d test images"
        % (tr.image_count_train, tr.image_count_test)
    )


def augment_img(img):
    """Color augmentation

    Args:
      img: input image

    Returns:
      img: augmented image
    """
    img = tf.image.random_hue(img, 0.08)
    img = tf.image.random_saturation(img, 0.6, 1.6)
    img = tf.image.random_brightness(img, 0.05)
    img = tf.image.random_contrast(img, 0.7, 1.3)
    return img


def augment_cmd(cmd):
    """
    Command augmentation

    Args:
      cmd: input command

    Returns:
      cmd: augmented command
    """
    if not (cmd > 0 or cmd < 0):
        coin = tf.random.uniform(
            shape=[1], minval=0, maxval=1, dtype=tf.dtypes.float32
        )
        if coin < 0.25:
            cmd = -1.0
        elif coin < 0.5:
            cmd = 1.0
    return cmd


def flip_sample(img, cmd, label):
    coin = tf.random.uniform(shape=[1], minval=0, maxval=1, dtype=tf.dtypes.float32)
    if coin < 0.5:
        img = tf.image.flip_left_right(img)
        cmd = -cmd
        label = tf.reverse(label, axis=[0])
    return img, cmd, label


def load_tfrecord_data(tr: Training, verbose=0):
    def parse_tfrecord_fn(example):
        feature_description = {
            "image": tf.io.FixedLenFeature([], tf.string),
            "path": tf.io.FixedLenFeature([], tf.string),
            "left": tf.io.FixedLenFeature([], tf.float32),
            "right": tf.io.FixedLenFeature([], tf.float32),
            "cmd": tf.io.FixedLenFeature([], tf.float32),
        }
        example = tf.io.parse_single_example(example, feature_description)
        img = tf.io.decode_jpeg(example["image"], channels=3)
        img = tf.image.convert_image_dtype(img, tf.float32)
        example["image"] = img
        return example

    def process_train_sample(features):
        #image = tf.image.resize(features["image"], size=(224, 224))
        image = features["image"]
        cmd  = features["cmd"]
        label = [features["left"], features["right"]]
        image = augment_img(image)
        if tr.hyperparameters.FLIP_AUG:
            img, cmd, label = flip_sample(img, cmd, label)
        if tr.hyperparameters.CMD_AUG:
            cmd = augment_cmd(cmd)

        return (image, cmd), label

    def process_test_sample(features):
        image = features["image"]
        cmd = features["cmd"]
        label = [features["left"], features["right"]]
        return (image, cmd), label

    train_dataset = ( 
        tf.data.TFRecordDataset(train_data_dir, num_parallel_reads=AUTOTUNE)
        .map(parse_tfrecord_fn, num_parallel_calls=AUTOTUNE)
        .map(process_train_sample, num_parallel_calls=AUTOTUNE)
    )

    # Obtains the images shapes of records from .tfrecords.
    for (image, cmd), label in train_dataset.take(1):
        shape = image.numpy().shape
        tr.NETWORK_IMG_HEIGHT = shape[0]
        tr.NETWORK_IMG_WIDTH = shape[1]
        print("Image shape: ", shape)
        print("Command: ", cmd.numpy())
        print("Label: ", label.numpy())

    # Obtains the total number of records from .tfrecords file
    # https://stackoverflow.com/questions/40472139/obtaining-total-number-of-records-from-tfrecords-file-in-tensorflow
    tr.image_count_train = sum(1 for _ in train_dataset)
    print ("Number of training instances: ", tr.image_count_train)

    # Prepare dataset for training
    tr.train_ds = (
        train_dataset.shuffle(tr.hyperparameters.TRAIN_BATCH_SIZE * 10)
        .repeat()
        .batch(tr.hyperparameters.TRAIN_BATCH_SIZE)
        .prefetch(AUTOTUNE)
    )

    tr.test_ds = (
        tf.data.TFRecordDataset(test_data_dir, num_parallel_reads=AUTOTUNE)
        .map(parse_tfrecord_fn, num_parallel_calls=AUTOTUNE)
        .map(process_test_sample, num_parallel_calls=AUTOTUNE)
        .shuffle(tr.hyperparameters.TRAIN_BATCH_SIZE * 10)
        .batch(tr.hyperparameters.TEST_BATCH_SIZE)
        .prefetch(AUTOTUNE)
    )


def load_data(tr: Training, verbose=0):
    # list_train_ds = tf.data.Dataset.list_files(train_frames)
    # list_test_ds = tf.data.Dataset.list_files(test_frames)
    list_train_ds = tf.data.Dataset.list_files(
        [str(train_data_dir + "/" + ds + "/*/images/*") for ds in tr.train_datasets]
    )
    list_test_ds = tf.data.Dataset.list_files(
        [str(test_data_dir + "/" + ds + "/*/images/*") for ds in tr.test_datasets]
    )
    train_data = dataloader.dataloader(train_data_dir, tr.train_datasets)
    test_data = dataloader.dataloader(test_data_dir, tr.test_datasets)

    if verbose:
        for f in list_train_ds.take(5):
            print(f.numpy())
        print()
        for f in list_test_ds.take(5):
            print(f.numpy())
        print("Number of train samples: %d" % len(train_data.labels))
        print("Number of test samples: %d" % len(test_data.labels))


    def process_train_path(file_path):
        cmd, label = train_data.get_label(
            tf.strings.regex_replace(file_path, "[/\\\\]", "/")
        )
        img = utils.load_img(file_path)
        img = augment_img(img)
        if tr.hyperparameters.FLIP_AUG:
            img, cmd, label = flip_sample(img, cmd, label)
        if tr.hyperparameters.CMD_AUG:
            cmd = augment_cmd(cmd)
        return (img, cmd), label

    def process_test_path(file_path):
        cmd, label = test_data.get_label(
            tf.strings.regex_replace(file_path, "[/\\\\]", "/")
        )
        img = utils.load_img(file_path)
        return (img, cmd), label

    # Set `num_parallel_calls` so multiple images are loaded/processed in parallel.
    labeled_ds = list_train_ds.map(process_train_path, num_parallel_calls=4)
    for (image, cmd), label in labeled_ds.take(1):
        shape = image.numpy().shape
        tr.NETWORK_IMG_HEIGHT = shape[0]
        tr.NETWORK_IMG_WIDTH = shape[1]
        print("Image shape: ", shape)
        print("Command: ", cmd.numpy())
        print("Label: ", label.numpy())
    tr.train_ds = utils.prepare_for_training(
        ds=labeled_ds,
        batch_sz=tr.hyperparameters.TRAIN_BATCH_SIZE,
        shuffle_buffer_sz=100 * tr.hyperparameters.TRAIN_BATCH_SIZE,
        prefetch_buffer_sz=10 * tr.hyperparameters.TRAIN_BATCH_SIZE,
    )
    (image_batch, cmd_batch), label_batch = next(iter(tr.train_ds))
    utils.show_train_batch(image_batch.numpy(), cmd_batch.numpy(), label_batch.numpy())
    savefig(os.path.join(models_dir, "train_preview.png"))
    test_ds = list_test_ds.map(process_test_path, num_parallel_calls=4)
    test_ds = test_ds.batch(tr.hyperparameters.TEST_BATCH_SIZE)
    tr.test_ds = test_ds.prefetch(buffer_size=10 * tr.hyperparameters.TRAIN_BATCH_SIZE)


def do_training(tr: Training, callback: tf.keras.callbacks.Callback, verbose=0):
    tr.model_name = dataset_name + "_" + str(tr.hyperparameters)
    tr.checkpoint_path = os.path.join(models_dir, tr.model_name, "checkpoints")
    tr.custom_objects = {'direction_metric':metrics.direction_metric, 'angle_metric':metrics.angle_metric}
    append_logs = False
    model: tf.keras.Model
    if tr.hyperparameters.USE_LAST:
        append_logs = True
        dirs = utils.list_dirs(tr.checkpoint_path)
        last_checkpoint = sorted(dirs)[-1]
        model = tf.keras.models.load_model(
            os.path.join(tr.checkpoint_path, last_checkpoint),
            custom_objects=tr.custom_objects,
            compile=False,
        )
    else:
        model = getattr(models, tr.hyperparameters.MODEL)(
            tr.NETWORK_IMG_WIDTH,
            tr.NETWORK_IMG_HEIGHT,
            tr.hyperparameters.BATCH_NORM,
        )

    tr.loss_fn = losses.sq_weighted_mse_angle
    tr.metric_list = ["mean_absolute_error", tr.custom_objects['direction_metric'], tr.custom_objects['angle_metric']]
    optimizer = tf.keras.optimizers.Adam(lr=tr.hyperparameters.LEARNING_RATE)

    model.compile(optimizer=optimizer, loss=tr.loss_fn, metrics=tr.metric_list)
    if verbose:
        print(model.summary())

    tr.log_path = os.path.join(models_dir, tr.model_name, "logs")
    if verbose:
        print(tr.model_name)

    STEPS_PER_EPOCH = np.ceil(
        tr.image_count_train / tr.hyperparameters.TRAIN_BATCH_SIZE
    )
    callback.broadcast("message", "Fit model...")
    tr.history = model.fit(
        tr.train_ds,
        epochs=tr.hyperparameters.NUM_EPOCHS,
        steps_per_epoch=STEPS_PER_EPOCH,
        validation_data=tr.test_ds,
        verbose=verbose,
        callbacks=[
            callbacks.checkpoint_cb(tr.checkpoint_path),
            callbacks.tensorboard_cb(tr.log_path),
            callbacks.logger_cb(tr.log_path, append_logs),
            callback,
        ],
    )


def do_evaluation(tr: Training, callback: tf.keras.callbacks.Callback, verbose=0):
    callback.broadcast("message", "Generate plots...")
    history = tr.history
    log_path = tr.log_path
    plt.plot(history.history["mean_absolute_error"], label="mean_absolute_error")
    plt.plot(history.history["val_mean_absolute_error"], label="val_mean_absolute_error")
    plt.xlabel("Epoch")
    plt.ylabel("Mean Absolute Error")
    plt.legend(loc="lower right")
    savefig(os.path.join(log_path, "error.png"))

    plt.plot(history.history["direction_metric"], label="direction_metric")
    plt.plot(history.history["val_direction_metric"], label="val_direction_metric")
    plt.xlabel("Epoch")
    plt.ylabel("Direction Metric")
    plt.legend(loc="lower right")
    savefig(os.path.join(log_path, "direction.png"))

    plt.plot(history.history["angle_metric"], label="angle_metric")
    plt.plot(history.history["val_angle_metric"], label="val_angle_metric")
    plt.xlabel("Epoch")
    plt.ylabel("Angle Metric")
    plt.legend(loc="lower right")
    savefig(os.path.join(log_path, "angle.png"))

    plt.plot(history.history["loss"], label="loss")
    plt.plot(history.history["val_loss"], label="val_loss")
    plt.xlabel("Epoch")
    plt.ylabel("Loss")
    plt.legend(loc="lower right")
    savefig(os.path.join(log_path, "loss.png"))

    callback.broadcast("message", "Generate tflite models...")
    checkpoint_path = tr.checkpoint_path
    print("checkpoint_path", checkpoint_path)
    best_index = np.argmax(
        np.array(history.history["val_angle_metric"])
        + np.array(history.history["val_direction_metric"])
    )
    best_checkpoint = str("cp-%04d.ckpt" % (best_index + 1))
    best_tflite = utils.generate_tflite(checkpoint_path, best_checkpoint)
    utils.save_tflite(best_tflite, checkpoint_path, "best")
    print(
        "Best Checkpoint (val_angle: %s, val_direction: %s): %s"
        % (
            history.history["val_angle_metric"][best_index],
            history.history["val_direction_metric"][best_index],
            best_checkpoint,
        )
    )

    last_checkpoint = sorted(utils.list_dirs(checkpoint_path))[-1]
    last_tflite = utils.generate_tflite(checkpoint_path, last_checkpoint)
    utils.save_tflite(last_tflite, checkpoint_path, "last")
    print(
        "Last Checkpoint (val_angle: %s, val_direction: %s): %s"
        % (
            history.history["val_angle_metric"][-1],
            history.history["val_direction_metric"][-1],
            last_checkpoint,
        )
    )

    callback.broadcast("message", "Evaluate model...")
    best_model = utils.load_model(
        os.path.join(checkpoint_path, best_checkpoint), tr.loss_fn, tr.metric_list, tr.custom_objects
    )
    # test_loss, test_acc, test_dir, test_ang = best_model.evaluate(tr.test_ds,
    res = best_model.evaluate(
        tr.test_ds,
        steps=tr.image_count_test / tr.hyperparameters.TEST_BATCH_SIZE,
        verbose=2,
    )
    print(res)

    NUM_SAMPLES = 15
    (image_batch, cmd_batch), label_batch = next(iter(tr.test_ds))
    pred_batch = best_model.predict(
        (
            tf.slice(image_batch, [0, 0, 0, 0], [NUM_SAMPLES, -1, -1, -1]),
            tf.slice(cmd_batch, [0], [NUM_SAMPLES]),
        )
    )
    utils.show_test_batch(
        image_batch.numpy(), cmd_batch.numpy(), label_batch.numpy(), pred_batch
    )
    savefig(os.path.join(log_path, "test_preview.png"))
    utils.compare_tf_tflite(best_model, best_tflite)


def savefig(path):
    plt.savefig(path, bbox_inches="tight")
    plt.clf()


def start_train(params: Hyperparameters, callback: MyCallback, verbose=0):
    tr = Training(params)
    if load_from_tf_record:
        callback.broadcast("message", "Loading data from tfrecord...")
        load_tfrecord_data(tr, verbose)
    else:
        callback.broadcast("message", "Processing data...")
        process_data(tr)
        callback.broadcast("message", "Loading data...")
        load_data(tr, verbose)
    callback.broadcast("preview")
    do_training(tr, callback, verbose)
    do_evaluation(tr, callback, verbose)

    return tr


if __name__ == "__main__":

    def broadcast(event, payload=None):
        print()
        print(event, payload)

    params = Hyperparameters()
    event = threading.Event()
    my_callback = MyCallback(broadcast, event)
    start_train(params, my_callback, verbose=1)
