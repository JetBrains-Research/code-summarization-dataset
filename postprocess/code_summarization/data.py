import os.path
import copy
from code_summarization.utils import read_file, json_dump


def pretty_dump(data, fileout):
    json_dump(data, fileout)


def get_all_field_items(data, field_name, to_set=False):
    items = [item[field_name] for item in data]
    items = [item for item in items if item is not None and item != 'null']
    if field_name == 'ast':  # unhashable type
        return items
    return list(set(items)) if to_set else items


def dump_all_fields(data, fields, dump_folder, to_set=False):
    summary_dump = []

    for field_name in fields:
        items = get_all_field_items(data, field_name, to_set=to_set)
        dump_path = dump_folder + os.path.sep + field_name + "s"
        dump_path += "_uniq" if to_set else ""
        dump_path += ".json"
        dump = {'field_name': field_name, 'is_uniq': to_set, 'count': len(items)}
        summary_dump.append(copy.deepcopy(dump))
        dump['items'] = items

        json_dump(dump, dump_path)

    json_dump(summary_dump, dump_folder + os.path.sep + "fields_summary.json")
