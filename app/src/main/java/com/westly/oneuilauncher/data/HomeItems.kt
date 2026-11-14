package com.westly.oneuilauncher.data

/** Something that sits in a home screen grid cell: one app, or a folder of apps. */
sealed interface HomeItem {
    val id: String

    data class App(val app: AppInfo) : HomeItem {
        override val id: String get() = app.key
    }

    data class Folder(val name: String, val apps: List<AppInfo>) : HomeItem {
        override val id: String get() = "folder:$name"
    }
}

object HomeItems {

    private class Rule(val name: String, val matches: (AppInfo) -> Boolean)

    // Apps are put in the first folder whose rule matches them.
    private val rules = listOf(
        Rule("Samsung") {
            it.packageName.startsWith("com.samsung.") || it.packageName.startsWith("com.sec.")
        },
        Rule("Google") {
            it.packageName.startsWith("com.google.") || it.packageName == "com.android.chrome"
        },
        Rule("Microsoft") {
            it.packageName.startsWith("com.microsoft.")
        }
    )

    /** A folder is only created when at least this many apps belong to it. */
    private const val MIN_FOLDER_SIZE = 2

    /** Folders first, then the remaining apps A to Z. */
    fun build(apps: List<AppInfo>): List<HomeItem> {
        val buckets = LinkedHashMap<String, MutableList<AppInfo>>()
        rules.forEach { buckets[it.name] = mutableListOf() }

        val loose = mutableListOf<AppInfo>()
        for (app in apps) {
            val rule = rules.firstOrNull { it.matches(app) }
            if (rule != null) {
                buckets.getValue(rule.name).add(app)
            } else {
                loose.add(app)
            }
        }

        val items = mutableListOf<HomeItem>()
        for ((name, list) in buckets) {
            if (list.size >= MIN_FOLDER_SIZE) {
                items.add(HomeItem.Folder(name, list))
            } else {
                loose.addAll(list)
            }
        }

        loose.sortBy { it.label.lowercase() }
        loose.forEach { items.add(HomeItem.App(it)) }
        return items
    }

    /**
     * Puts items in the order the user saved by dragging. Items not in the saved order
     * (new apps) keep their normal position after the ones that are.
     */
    fun applyOrder(items: List<HomeItem>, savedOrder: List<String>): List<HomeItem> {
        if (savedOrder.isEmpty()) return items
        val position = HashMap<String, Int>()
        savedOrder.forEachIndexed { index, id -> position[id] = index }
        return items.withIndex()
            .sortedWith(
                compareBy<IndexedValue<HomeItem>>(
                    { position[it.value.id] ?: Int.MAX_VALUE },
                    { it.index }
                )
            )
            .map { it.value }
    }
}
