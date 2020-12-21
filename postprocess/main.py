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
fields = ["name", "full_name", "file", "body", "comment", "doc", "ast"]

REPO_DELIMITER = "__"
METHODS_DUMP = "methods.jsonl"
METHODS_PRETTY = "methods_pretty.json"
DUMP_FOLDER = "post_process_summary"


def traverse_analysis_results(analysis_dir):
    for root, dirs, files in os.walk(analysis_dir):
        if REPO_DELIMITER not in root:
            continue

        if METHODS_DUMP in files:
            dump_folder = root + os.path.sep + DUMP_FOLDER
            fields_uniq_dump_path = dump_folder + os.path.sep + "fields_uniq"
            fields_not_uniq_dump_path = dump_folder + os.path.sep + "fields_not_uniq"
            methods_dump_path = root + os.path.sep + METHODS_DUMP

            remove_if_exists(dump_folder)
            create_dir(dump_folder)
            create_dir(fields_uniq_dump_path)
            create_dir(fields_not_uniq_dump_path)

            repo_name = root.split(os.path.sep)
            size = get_kb_file_size(methods_dump_path)
            if len(repo_name) > 0:
                print(f"> postprocess [{size} KB] /{root.split(os.path.sep)[-1]}")
            else:
                print(f"> postprocess [{size} KB] /{root}")

            data = read_file(methods_dump_path)
            pretty_dump(data, dump_folder + os.path.sep + METHODS_PRETTY)
            dump_all_fields(data, fields, fields_uniq_dump_path, True)
            dump_all_fields(data, fields, fields_not_uniq_dump_path, False)


if __name__ == '__main__':
    repos_path = parse_args()
    traverse_analysis_results(repos_path)
