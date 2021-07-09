import pygame
from pygame.locals import *
import cv2 # pip3 install opencv-python
import os
import threading
import json
from common import *
import argparse


s_socket = ServerSocket()

white = (255, 255, 255)
black = (0, 0, 0)
blue = (0, 0, 128)
red = (200, 0, 0)

class CommandHandler:

    def __init__(self):
        pygame.key.set_repeat(200)

    left = DriveValue()
    right = DriveValue()

    def send_command(self, command):
        s_socket.send('{{command: {command} }}\n'.format(command=command))

    def send_drive_command(self, left, right):
        s_socket.send('{{driveCmd: {{l:{l}, r:{r} }} }}\n'.format(l=left, r=right))

    def reset(self):
        self.send_drive_command(self.left.reset(), self.right.reset())

    def forward_left(self):
        self.send_drive_command(self.left.write(0.75), self.right.max())

    def forward_right(self):
        self.send_drive_command(self.left.max(), self.right.write(0.75))

    def backward_left(self):
        self.send_drive_command(self.left.min(), self.right.write(-0.75))

    def backward_right(self):
        self.send_drive_command(self.left.write(-0.75), self.right.min())

    def rotate_left(self):
        self.send_drive_command(self.left.min(), self.right.max())

    def rotate_right(self):
        self.send_drive_command(self.left.max(), self.right.min())

    def go_forward(self):
        self.send_drive_command(self.left.max(), self.right.max())

    def go_backward(self):
        self.send_drive_command(self.left.min(), self.right.min())

    def handle_keys(self):
        while True:
            for event in pygame.event.get():
                if event.type == pygame.QUIT:
                    running = False

                if event.type == pygame.KEYUP:
                    if event.key==K_w:
                        self.reset()

                    elif event.key in [K_a, K_d, K_q, K_e]:
                        if(pygame.key.get_pressed()[pygame.K_w]):
                            self.go_forward()
                        elif(pygame.key.get_pressed()[pygame.K_s]):
                            self.go_backward()    
                        else:
                            self.reset()

                    elif event.key==K_s:
                        self.reset()

                if event.type == pygame.KEYDOWN:
                    if event.key==K_w:
                        self.go_forward()

                    elif event.key==K_s:
                        self.go_backward()

                    elif event.key==K_a:
                        if(pygame.key.get_pressed()[pygame.K_w]):
                            self.forward_left()
                        elif(pygame.key.get_pressed()[pygame.K_s]):
                            self.backward_left()

                    elif event.key==K_d:
                        if(pygame.key.get_pressed()[pygame.K_w]):
                            self.forward_right()
                        elif(pygame.key.get_pressed()[pygame.K_s]):
                            self.backward_right()

                    elif event.key==K_q:
                        self.rotate_left()

                    elif event.key==K_e:
                        self.rotate_right()

                    if event.key==pygame.K_n: self.send_command("NOISE")
                    if event.key==pygame.K_SPACE: self.send_command("LOGS")
                    if event.key==pygame.K_RIGHT: self.send_command("INDICATOR_RIGHT")
                    if event.key==pygame.K_LEFT: self.send_command("INDICATOR_LEFT")
                    if event.key==pygame.K_UP: self.send_command("INDICATOR_STOP")
                    if event.key==pygame.K_DOWN: self.send_command("NETWORK")
                    if event.key==pygame.K_m: self.send_command("DRIVE_MODE")
                    if event.key==pygame.K_ESCAPE:
                        return

(zc, info) = register("OPEN_BOT_CONTROLLER", 19400)

class VideoPlayer:
    def set_stream (self, stream):
        self.stream = stream

    def play_video(self):
        if not self.stream:
            print(f'Stream not set')
            return

        print(f'Opening the stream...')
        cap = cv2.VideoCapture(self.stream)

        # read one frame and check if there was no problem
        print(f'Checking the stream...')
        ret, img = cap.read()
        if not ret:
            print("Can't read stream")
            cap.release()
            cv2.destroyAllWindows()
            return

        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        img = cv2.transpose(img)

        # display its width, height, color_depth
        print('Stream resolution:', img.shape)
        shape = img.shape
        width = img.shape[0]
        height = img.shape[1]
    
        display_flags = DOUBLEBUF | HWSURFACE # | SCALED
        if pygame.display.mode_ok(size=(width, height), flags=display_flags ):
            video_screen = pygame.display.set_mode(size=(width, height), flags=display_flags)
        else:
            raise ValueError("error initializing display, can not get mode")

        print (f'Display: {pygame.display.Info()}')

        running = True
        while running:

            # read one frame and check if there was no problem
            ret, img = cap.read()
            if not ret:
                running = False
                cap.release()
                cv2.destroyAllWindows()
                break

            else:
                img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
                img = cv2.transpose(img)

                # blit directly on screen         
                pygame.surfarray.blit_array(video_screen, img)

            pygame.display.flip()

    def stop_video(self):
        print(f'Stopping video...')
        pass

def usage():
    usageStr = """
    Make sure to keep the pygame window in focus!\r
    
    Use the following keys to drive the robot:\r

    \tW:        Go forward\r
    \tS:        Go backward\r
    \tA:        Turn slightly left (while driving)\r
    \tD:        Turn slightly right (while driving)\r
    \tQ:        Rotate left\r
    \tE:        Rotate right\r

    \tM:        Drive mode\r
    \tN:        Toggle noise\r
    \tLeft:     Left indicator\r
    \tRight:    Right indicator\r
    \tUp:       Cancel indicators\r
    \tDown:     Network mode\r
    \tSPACE:    Toggle logging\r
    \tESC:      Quit\r
    """

    return usageStr

def run_receiver ():
    while True:
        try:
            data = s_socket.receive()
            print(f'Received: {data}\r')

            if data in ["", None]:
                return

            handle_status(data)

        except Exception as e:
            print(f'run_receiver: Got exception: {e}\r')
            break

video_player = VideoPlayer()

def handle_status(data):
    parsed_data = json.loads(data)
    if not 'status' in parsed_data:
        return

    status = parsed_data['status']

    try:
        if 'VIDEO_SERVER_URL' in status:
            stream = status ['VIDEO_SERVER_URL']
            video_player.set_stream(stream)

        if 'VIDEO_COMMAND' in status:
            if status['VIDEO_COMMAND'] == 'START':
                print(f'Starting video...')
                video_player.play_video()
            if status['VIDEO_COMMAND'] == 'STOP':
                video_player.stop_video()

        if 'VIDEO_PROTOCOL' in status:
            if status['VIDEO_PROTOCOL'] == 'WEBRTC':
                screen.add_text("WebRTC video not supported. Please set your Android app to use RTSP.")

    except Exception as e:
        print (f"handle_status exception: {e}")

class Screen:

    screen = None
    font = None
    y_pos = 0
    x_pos = 0

    def setup_screen (self):
        pygame.display.set_caption('OpenBot keyboard controller')
        self.font = pygame.font.Font(None, 32) #Use system font
        self.screen = pygame.display.set_mode([1280, 760], pygame.RESIZABLE)
        self.screen.fill(white)
        text = usage()
        print(text)
        lines = text.strip().split('\r')
        self.x_pos = 50
        self.y_pos = 50
        delimiter=':'
        for line in lines:
            # create a text suface object
            if delimiter in line:
                space = '   ' if '\t' in line else ''
                elements = line.strip().split(delimiter)
                text = self.font.render(space + elements[0].strip() + delimiter, True, blue)
                self.screen.blit(text, (self.x_pos, self.y_pos))
                text = self.font.render(elements[1].strip(), True, black)
                self.screen.blit(text, (self.x_pos+200, self.y_pos))
            else:
                text = self.font.render(line, True, red)
                self.screen.blit(text, (self.x_pos, self.y_pos))
            pygame.display.update()
            self.y_pos += 40

    def add_text(self, message):
        text = self.font.render(message, True, red)
        self.y_pos += 20
        self.screen.blit(text, (self.x_pos, self.y_pos))
        pygame.display.update()


screen = Screen()

def run(args):

    os.environ["OPENCV_FFMPEG_CAPTURE_OPTIONS"] = "rtsp_transport;udp"

    print('Waiting for connection...\r\n')
    s_socket.accept()
    print('Connected! 😃\n\r')

    pygame.init()
    screen.setup_screen()

    if (args.video):
        t = threading.Thread(target=run_receiver)
        t.start()

    cmd_handler = CommandHandler ()
    cmd_handler.handle_keys ()

    s_socket.close()
    zc.unregister_service(info)
    zc.close()
    print('Exiting...\r\n')

if __name__ == "__main__":
    # cli
    parser = argparse.ArgumentParser()
    parser.add_argument("-v", "--video", action="store_true", help="video stream")

    args = parser.parse_args()
    run (args)