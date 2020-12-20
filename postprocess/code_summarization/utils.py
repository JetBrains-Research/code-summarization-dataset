import json
import os

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
