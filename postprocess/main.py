import sys
import os
import os.path
from code_summarization.data import *
from code_summarization.utils import *


class BadArgumentsException(Exception):
    pass


def parse_args():
    args = sys.argv
    if len(args) != 2:
        raise BadArgumentsException("number of args isn't 1")
    if not os.path.exists(args[1]):
        raise FileExistsError(f"repo folder '{args[1]}' doesn't exist")
    return args[1]


DUMP_FOLDER = "post_proc_summary"

# interesting fields
is_uniq_fields = False
fields = ["name", "full_name", "file", "body", "comment", "doc", "ast"]


if __name__ == '__main__':
    repo_path = parse_args()
    dump_path = repo_path + os.path.sep + DUMP_FOLDER
    dump_path_fields = dump_path + os.path.sep + "fields"
    create_dir(dump_path)
    create_dir(dump_path_fields)

    data = read_file(repo_path + os.path.sep + "methods.jsonl")
    pretty_dump(data, dump_path + os.path.sep + "methods_pretty.json")
    dump_all_fields(data, fields, dump_path_fields, is_uniq_fields)
