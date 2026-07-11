from pathlib import Path

source_path = Path(__file__).parent / "src/main/java/com/leonardo/edgestopwatch/StopwatchService.java"
source = source_path.read_text(encoding="utf-8")

replacements = [
    (
        "new LinearLayout.LayoutParams(0, scaledDp(50), 1f));",
        "new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));",
    ),
    (
        """copy.addView(value, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    scaledDp(35)));""",
        """copy.addView(value, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));""",
    ),
    (
        """dividerParams.leftMargin = scaledDp(10);
            dividerParams.rightMargin = scaledDp(10);""",
        """dividerParams.leftMargin = scaledDp(10);
            dividerParams.rightMargin = scaledDp(10);
            dividerParams.topMargin = scaledDp(5);
            dividerParams.bottomMargin = scaledDp(5);""",
    ),
    (
        """timeParams.height = scaledDp(50);
        timeView.setLayoutParams(timeParams);""",
        """timeParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        timeView.setLayoutParams(timeParams);
        timeView.setMinimumHeight(scaledDp(64));""",
    ),
    (
        "timer.rowView.setMinimumHeight(scaledDp(56));",
        "timer.rowView.setMinimumHeight(scaledDp(72));",
    ),
    (
        """valueParams.height = scaledDp(35);
            timer.valueView.setLayoutParams(valueParams);""",
        """valueParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            timer.valueView.setLayoutParams(valueParams);
            timer.valueView.setMinimumHeight(scaledDp(50));""",
    ),
    (
        """float thickness = Math.max(1f, Math.min(scaledDp(2), step * 0.48f));
            float markLength = Math.min(getWidth(), scaledDp(18));""",
        """float thickness = Math.max(1f, Math.min(scaledDp(3), step * 0.65f));
            float markLength = Math.min(getWidth(), scaledDp(30));""",
    ),
]

changed = False
for old, new in replacements:
    old_matches = source.count(old)
    new_matches = source.count(new)
    if old_matches == 1:
        source = source.replace(old, new, 1)
        changed = True
    elif old_matches == 0 and new_matches >= 1:
        continue
    else:
        raise RuntimeError(
            f"Unexpected replacement state: old={old_matches}, new={new_matches}, text={old[:90]!r}"
        )

if changed:
    source_path.write_text(source, encoding="utf-8")
    print("Applied stopwatch layout hotfix")
else:
    print("Stopwatch layout hotfix already applied")
