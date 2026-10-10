package dev.harness.tools

/** Lookup over the set of tools exposed to the model. */
class ToolRegistry(private val tools: List<Tool>) {

    private val byName: Map<String, Tool> = tools.associateBy { it.name }

    init {
        require(byName.size == tools.size) {
            val dupes = tools.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
            "duplicate tool name(s): $dupes"
        }
    }

    fun find(name: String): Tool? = byName[name]

    fun all(): List<Tool> = tools

    /** Declarations passed to the LLM in declaration order. */
    fun schemas(): List<ToolSchema> = tools.map { ToolSchema(it.name, it.description, it.jsonSchema) }

    companion object {
        val Empty = ToolRegistry(emptyList())
    }
}
