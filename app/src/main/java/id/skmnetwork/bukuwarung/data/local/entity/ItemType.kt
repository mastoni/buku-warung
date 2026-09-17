package id.skmnetwork.bukuwarung.data.local.entity

enum class ItemType {
    PHYSICAL,
    SERVICE,
    DIGITAL,
    FUEL;

    val isStockable: Boolean
        get() = this == PHYSICAL || this == FUEL

    companion object {
        fun isStockable(typeName: String?): Boolean {
            return typeName == PHYSICAL.name || typeName == FUEL.name
        }
    }
}
