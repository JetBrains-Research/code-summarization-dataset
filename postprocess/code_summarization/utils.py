import json
import os
from shutil import rmtree


def read_file(filepath):
    lines = []
    with open(filepath) as file:
        for line in file:
            lines.append(json.loads(line))
    return lines


def json_dump(content, filepath):
    with open(filepath, 'w') as file:
        json.dump(content, file, indent=4)


def create_dir(path):
    if not os.path.exists(path):
        os.mkdir(path)


def remove_if_exists(path):
    if os.path.exists(path):
        if os.path.isdir(path):
            rmtree(path)
        elif os.path.isfile(path):
            os.remove(path)


def get_kb_file_size(path):
    if os.path.exists(path):
        size = os.path.getsize(path)
        if size % 1024 == 0:
            return os.path.getsize(path) / 1024
        return int((os.path.getsize(path) + 1024) / 1024)

    return 0
