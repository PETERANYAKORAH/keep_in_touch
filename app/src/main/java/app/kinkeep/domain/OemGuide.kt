package app.kinkeep.domain

/** Phone makers that stop background apps more aggressively than stock Android. */
enum class Oem { SAMSUNG, XIAOMI, OPPO, VIVO, HUAWEI, TRANSSION, OTHER }

object OemGuide {
    fun detect(manufacturer: String?, brand: String? = null): Oem {
        val text = "${manufacturer.orEmpty()} ${brand.orEmpty()}".lowercase()
        fun has(vararg names: String) = names.any { it in text }
        return when {
            has("xiaomi", "redmi", "poco") -> Oem.XIAOMI
            has("samsung") -> Oem.SAMSUNG
            has("oppo", "realme", "oneplus") -> Oem.OPPO
            has("vivo", "iqoo") -> Oem.VIVO
            has("huawei", "honor") -> Oem.HUAWEI
            has("tecno", "infinix", "itel", "transsion") -> Oem.TRANSSION
            else -> Oem.OTHER
        }
    }
}
