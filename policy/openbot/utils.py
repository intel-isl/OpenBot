# Created by Matthias Mueller - Intel Intelligent Systems Lab - 2020

import csv
import matplotlib.pyplot as plt
import tensorflow as tf
import numpy as np
import os
from IPython.display import Javascript
from nbconvert import HTMLExporter
import codecs
import nbformat


def save_notebook():
    Javascript("IPython.notebook.save_notebook()")


def output_HTML(read_file, output_file):
    exporter = HTMLExporter()
    # read_file is '.ipynb', output_file is '.html'
    output_notebook = nbformat.read(read_file, as_version=4)
    output, resources = exporter.from_notebook_node(output_notebook)
    codecs.open(output_file, "w", encoding="utf-8").write(output)


def prepare_for_training(
    ds, batch_sz, shuffle_buffer_sz=10000, prefetch_buffer_sz=1000, cache=True
):
    # This is a small dataset, only load it once, and keep it in memory.
    # use `.cache(filename)` to cache preprocessing work for datasets that don't
    # fit in memory.
    if cache:
        if isinstance(cache, str):
            ds = ds.cache(cache)
        else:
            ds = ds.cache()

    ds = ds.shuffle(buffer_size=shuffle_buffer_sz)

    # Repeat forever
    ds = ds.repeat()

    ds = ds.batch(batch_sz)

    # `prefetch` lets the dataset fetch batches in the background while the model
    # is training.
    ds = ds.prefetch(buffer_size=prefetch_buffer_sz)

    return ds


def show_train_batch(image_batch, cmd_batch, label_batch, fig_num=1):
    plt.figure(num=fig_num, figsize=(15, 10))
    for n in range(15):
        ax = plt.subplot(5, 3, n + 1)
        plt.imshow(image_batch[n])
        plt.title(
            "Cmd: %s, Label: [%.2f %.2f]"
            % (cmd_batch[n], float(label_batch[n][0]), float(label_batch[n][1]))
        )
        plt.axis("off")


def show_test_batch(image_batch, cmd_batch, label_batch, pred_batch, fig_num=1):
    plt.figure(num=fig_num, figsize=(15, 10))
    for n in range(15):
        ax = plt.subplot(5, 3, n + 1)
        plt.imshow(image_batch[n])
        plt.title(
            "Cmd: %s, Label: [%.2f %.2f], Pred: [%.2f %.2f]"
            % (
                cmd_batch[n],
                float(label_batch[n][0]),
                float(label_batch[n][1]),
                float(pred_batch[n][0]),
                float(pred_batch[n][1]),
            )
        )
        plt.axis("off")


def generate_tflite(path, filename):
    converter = tf.lite.TFLiteConverter.from_saved_model(os.path.join(path, filename))
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()
    return tflite_model


def save_tflite(tflite_model, path, filename):
    open(os.path.join(path, filename + ".tflite"), "wb").write(tflite_model)


def load_tflite(*parts):
    interpreter = tf.lite.Interpreter(model_path=os.path.join(*parts))
    return interpreter


def load_model(model_path, loss_fn, metric_list, custom_objects):
    model: tf.keras.Model = tf.keras.models.load_model(
        model_path, custom_objects=custom_objects, compile=False
    )
    model.compile(loss=loss_fn, metrics=metric_list)
    return model


def compare_tf_tflite(model, tflite_model, img=None, cmd=None):
    # Load TFLite model and allocate tensors.
    interpreter = tf.lite.Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()

    # Get input and output tensors.
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    # Test the TensorFlow Lite model on input data. If no data provided, generate random data.
    input_data = {}
    for input_detail in input_details:
        if input_detail["name"] == "img_input" and img is not None:
            print(img)
            input_data[input_detail["name"]] = img
        elif input_detail["name"] == "cmd_input" and cmd is not None:
            print(cmd)
            input_data[input_detail["name"]] = cmd[0]
        else:
            input_data[input_detail["name"]] = np.array(
                np.random.random_sample(input_detail["shape"]), dtype=np.float32
            )
        interpreter.set_tensor(input_detail["index"], input_data[input_detail["name"]])

    interpreter.invoke()

    # The function `get_tensor()` returns a copy of the tensor data.
    # Use `tensor()` in order to get a pointer to the tensor.
    tflite_results = interpreter.get_tensor(output_details[0]["index"])
    print("tflite:", tflite_results)

    # Test the TensorFlow model on random input data.
    tf_results = model.predict(
        (tf.constant(input_data["img_input"]), tf.constant(input_data["cmd_input"]))
    )
    print("tf:", tf_results)

    # Compare the result.
    for tf_result, tflite_result in zip(tf_results, tflite_results):
        print(
            "Almost equal (5% tolerance):",
            np.allclose(tf_result, tflite_result, rtol=5e-02),
        )
        # np.testing.assert_almost_equal(tf_result, tflite_result, decimal=2)


def list_dirs(path):
    return [d for d in os.listdir(path) if os.path.isdir(os.path.join(path, d))]


def load_img(file_path):
    # load the raw data from the file as a string
    img = tf.io.read_file(file_path)
    # convert the compressed string to a 3D uint8 tensor
    img = tf.image.decode_jpeg(img, channels=3)
    # Use `convert_image_dtype` to convert to floats in the [0,1] range.
    img = tf.image.convert_image_dtype(img, tf.float32)
    return img


def read_csv_dict(csv_path):
    logs = []
    with open(csv_path, newline="") as csvfile:
        reader = csv.DictReader(csvfile)
        for row in reader:
            logs.append(row)
    return logs
