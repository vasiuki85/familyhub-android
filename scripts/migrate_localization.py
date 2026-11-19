#!/usr/bin/env python3
"""Migrate default strings to English (values/) and Russian (values-ru/)."""

from __future__ import annotations

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
VALUES = RES / "values"
VALUES_RU = RES / "values-ru"

CYRILLIC = re.compile(r"[А-Яа-яЁё]")
SKIP_RU_KEYS = {"settings_family_create_button", "settings_family_join_button"}

TOKEN_PATTERN = re.compile(
    r"(%(?:\d+\$)?[sdif]|%[sdif]|&[a-zA-Z]+;|&#\d+;)",
)


def load_existing_ru_keys() -> set[str]:
    keys: set[str] = set()
    for path in VALUES_RU.glob("strings*.xml"):
        if path.name == "strings.xml":
            continue
        root = ET.parse(path).getroot()
        keys.update(el.attrib["name"] for el in root.findall("string"))
    return keys


def tokenize(text: str) -> tuple[str, list[str]]:
    tokens: list[str] = []

    def repl(match: re.Match[str]) -> str:
        tokens.append(match.group(0))
        return f"__PH_{len(tokens) - 1}__"

    return TOKEN_PATTERN.sub(repl, text), tokens


def detokenize(text: str, tokens: list[str]) -> str:
    for index, token in enumerate(tokens):
        text = text.replace(f"__PH_{index}__", token)
        text = text.replace(f"__Ph_{index}__", token)
        text = text.replace(f"__ph_{index}__", token)
    return text


def translate_batch(texts: list[str], translator) -> list[str]:
    if not texts:
        return []
    protected_batch = []
    token_maps = []
    for text in texts:
        protected, tokens = tokenize(text)
        protected_batch.append(protected)
        token_maps.append(tokens)
    joined = "\n|||SPLIT|||\n".join(protected_batch)
    translated = translator.translate(joined)
    parts = translated.split("\n|||SPLIT|||\n")
    if len(parts) != len(texts):
        raise RuntimeError(
            f"Translation split mismatch: expected {len(texts)}, got {len(parts)}"
        )
    return [detokenize(part, tokens) for part, tokens in zip(parts, token_maps)]


def write_xml(path: Path, root: ET.Element) -> None:
    ET.indent(root, space="    ")
    tree = ET.ElementTree(root)
    tree.write(path, encoding="utf-8", xml_declaration=True)
    # ElementTree uses single quotes in declaration on some versions; normalize
    text = path.read_text(encoding="utf-8")
    if text.startswith("<?xml version='1.0'"):
        text = text.replace("<?xml version='1.0' encoding='utf-8'?>", '<?xml version="1.0" encoding="utf-8"?>', 1)
        path.write_text(text, encoding="utf-8", newline="\n")


def migrate_main_strings(translator) -> None:
    source = VALUES / "strings.xml"
    ru_target = VALUES_RU / "strings.xml"
    existing_ru = load_existing_ru_keys()

    tree = ET.parse(source)
    root = tree.getroot()

    ru_root = ET.Element("resources")
    to_translate: list[tuple[ET.Element, str]] = []

    for element in root.findall("string"):
        name = element.attrib["name"]
        value = element.text or ""
        if name not in existing_ru and name not in SKIP_RU_KEYS:
            ru_el = ET.SubElement(ru_root, "string", {"name": name})
            ru_el.text = value
        if CYRILLIC.search(value):
            to_translate.append((element, value))

    write_xml(ru_target, ru_root)
    print(f"Wrote {ru_target}")

    batch_size = 25
    for index in range(0, len(to_translate), batch_size):
        chunk = to_translate[index : index + batch_size]
        translated = translate_batch([value for _, value in chunk], translator)
        for (element, _), new_value in zip(chunk, translated):
            element.text = new_value
        print(f"Translated {min(index + batch_size, len(to_translate))}/{len(to_translate)}")
        time.sleep(0.15)

    write_xml(source, root)
    print(f"Updated {source}")


def patch_file(path: Path, mapping: dict[str, str]) -> None:
    tree = ET.parse(path)
    root = tree.getroot()
    for element in root.findall("string"):
        name = element.attrib["name"]
        if name in mapping:
            element.text = mapping[name]
    write_xml(path, root)
    print(f"Patched {path.name}")


def append_ru_strings(path: Path, mapping: dict[str, str]) -> None:
    tree = ET.parse(path)
    root = tree.getroot()
    existing = {el.attrib["name"] for el in root.findall("string")}
    for name, text in mapping.items():
        if name in existing:
            continue
        el = ET.SubElement(root, "string", {"name": name})
        el.text = text
    write_xml(path, root)
    print(f"Extended {path.name}")


def fix_partial_files() -> None:
    ui_en = {
        "recipes_category_all": "All",
        "recipes_category_first": "Soups & starters",
        "recipes_category_second": "Main courses",
        "recipes_category_snacks": "Snacks",
        "recipes_category_salads": "Salads",
        "recipes_field_category": "Category",
        "task_reward_points_label": "Reward points",
        "task_reward_points_hint": "Points the assignee will earn",
        "menu_template_default_name": "Menu template",
        "menu_template_saved": "Template saved",
        "menu_template_applied": "Template \"%1$s\" applied",
        "menu_template_save_title": "Save template",
        "menu_template_name_hint": "Template name",
        "menu_template_save_button": "Save",
        "menu_template_load_title": "Load template",
        "menu_template_load_empty": "No saved templates yet",
        "menu_template_load_button": "Load",
        "menu_assign_dialog_title_day": "Assign cook — %1$s",
        "menu_preferences_reward_prompt_time": "Reminder time",
        "menu_preferences_reward_prompt_time_hint": "Hour (0–23)",
        "menu_clear_icon_cd": "Clear menu",
        "menu_export_week_to_shopping_icon_cd": "Export week to shopping list",
        "menu_preferences_icon_cd": "Configure preferences",
        "menu_share_pdf_icon_cd": "Export to PDF",
        "menu_share_image_icon_cd": "Export as image",
        "menu_notify_today_icon_cd": "Remind for today",
        "menu_edit_meal_cd": "Edit meal",
        "menu_add_meal_cd": "Add to menu",
        "task_confirmation_pending": "Waiting for parent approval",
        "task_confirm_completion_cd": "Confirm completion",
        "task_decline_completion_cd": "Decline completion",
        "task_confirm_completion_title": "Confirm completion",
        "task_confirm_completion_message": "Confirm completion of \"%1$s\"?",
        "task_sent_for_approval": "Task sent for approval",
        "menu_reward_prompt_title": "Rate the cook — %1$s",
        "menu_reward_prompt_message": "Enter points for completing today's menu",
        "menu_reward_prompt_points_label": "Points",
        "menu_reward_prompt_confirm": "Confirm",
        "menu_reward_prompt_cancel": "Cancel",
        "menu_reward_prompt_success": "Rating saved",
        "menu_reward_prompt_error": "Could not save rating",
    }
    ui_ru = {
        "recipes_category_all": "Все",
        "recipes_category_first": "Первые блюда",
        "recipes_category_second": "Вторые блюда",
        "recipes_category_snacks": "Закуски",
        "recipes_category_salads": "Салаты",
        "recipes_field_category": "Категория",
        "task_reward_points_label": "Вознаграждение, баллов",
        "task_reward_points_hint": "Сколько баллов получит исполнитель",
        "menu_template_default_name": "Шаблон меню",
        "menu_template_saved": "Шаблон сохранён",
        "menu_template_applied": "Шаблон \"%1$s\" применён",
        "menu_template_save_title": "Сохранить шаблон",
        "menu_template_name_hint": "Название шаблона",
        "menu_template_save_button": "Сохранить",
        "menu_template_load_title": "Загрузить шаблон",
        "menu_template_load_empty": "Сохранённых шаблонов пока нет",
        "menu_template_load_button": "Загрузить",
        "menu_assign_dialog_title_day": "Назначить повара — %1$s",
        "menu_preferences_reward_prompt_time": "Время напоминания",
        "menu_preferences_reward_prompt_time_hint": "Час (0–23)",
        "menu_clear_icon_cd": "Очистить меню",
        "menu_export_week_to_shopping_icon_cd": "Экспортировать в покупки недели",
        "menu_preferences_icon_cd": "Настроить предпочтения",
        "menu_share_pdf_icon_cd": "Экспорт в PDF",
        "menu_share_image_icon_cd": "Экспорт как изображение",
        "menu_notify_today_icon_cd": "Напомнить на сегодня",
        "menu_edit_meal_cd": "Редактировать блюдо",
        "menu_add_meal_cd": "Добавить в меню",
        "task_confirmation_pending": "Ожидает подтверждения родителя",
        "task_confirm_completion_cd": "Подтвердить выполнение",
        "task_decline_completion_cd": "Отклонить выполнение",
        "task_confirm_completion_title": "Подтвердить выполнение",
        "task_confirm_completion_message": "Подтвердить выполнение задачи «%1$s»?",
        "task_sent_for_approval": "Задача отправлена на подтверждение",
        "menu_reward_prompt_title": "Оценить повара — %1$s",
        "menu_reward_prompt_message": "Укажите количество баллов за выполнение сегодняшнего меню",
        "menu_reward_prompt_points_label": "Баллы",
        "menu_reward_prompt_confirm": "Подтвердить",
        "menu_reward_prompt_cancel": "Отмена",
        "menu_reward_prompt_success": "Оценка сохранена",
        "menu_reward_prompt_error": "Не удалось сохранить оценку",
    }
    tasks_rewards_en = {
        "tasks_group_overdue": "Overdue",
        "tasks_group_today": "Today",
        "tasks_group_tomorrow": "Tomorrow",
        "tasks_group_later": "Later",
        "tasks_group_no_date": "No due date",
        "tasks_group_todo": "To do",
        "tasks_group_waiting": "Awaiting approval",
        "tasks_group_done": "Done",
        "tasks_group_count": "%1$d",
        "tasks_filters_section_title": "Search and filters",
        "tasks_editor_essentials_title": "Essentials",
        "tasks_editor_additional": "More options",
        "tasks_editor_additional_expanded_cd": "Hide additional options",
        "tasks_editor_additional_collapsed_cd": "Show additional options",
        "tasks_editor_close_cd": "Close task editor",
        "tasks_editor_save_cd": "Save task",
        "rewards_hero_title": "Earn points together",
        "rewards_hero_subtitle": "Pick a fun goal and track your progress",
        "rewards_progress_remaining": "%1$d points to go",
        "rewards_progress_ready": "Reward unlocked!",
        "rewards_progress_cd": "%1$d of %2$d points earned",
        "rewards_catalog_item_cd": "%1$s, costs %2$d points",
        "rewards_inactive_friendly_hint": "This reward is paused for now",
    }
    calendar_en = {
        "calendar_sync_grant_scope_button": "Allow calendar access",
        "calendar_sync_grant_scope_message": "Google Calendar access is required to load your calendars. Please grant permission.",
        "calendar_sync_done": "Sync completed",
        "calendar_sync_error_sign_in_detailed": "Google sign-in failed: %1$s (%2$d)",
    }
    calendar_ru = {
        "calendar_sync_grant_scope_button": "Разрешить доступ к календарю",
        "calendar_sync_grant_scope_message": "Для загрузки списков календарей требуется доступ к Google Календарю. Пожалуйста, разрешите доступ.",
        "calendar_sync_done": "Синхронизация выполнена",
        "calendar_sync_error_sign_in_detailed": "Вход через Google не удался: %1$s (%2$d)",
    }

    patch_file(VALUES / "strings_ui.xml", ui_en)
    append_ru_strings(VALUES_RU / "strings_ui.xml", ui_ru)
    patch_file(VALUES / "strings_redesign_tasks_rewards.xml", tasks_rewards_en)
    patch_file(VALUES / "strings_calendar_sync.xml", calendar_en)

    ru_calendar = VALUES_RU / "strings_calendar_sync.xml"
    root = ET.Element("resources")
    for name, text in calendar_ru.items():
        el = ET.SubElement(root, "string", {"name": name})
        el.text = text
    write_xml(ru_calendar, root)


def ensure_translator():
    try:
        from deep_translator import GoogleTranslator

        return GoogleTranslator(source="ru", target="en")
    except ImportError:
        subprocess.check_call(
            [sys.executable, "-m", "pip", "install", "deep-translator", "-q"]
        )
        from deep_translator import GoogleTranslator

        return GoogleTranslator(source="ru", target="en")


def main() -> int:
    translator = ensure_translator()
    migrate_main_strings(translator)
    fix_partial_files()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
