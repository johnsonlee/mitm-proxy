package io.johnsonlee.mitmproxy.internal.ssl

import java.io.File

data class Authority(
        val alias: String = "mitmproxy",
        val password: CharArray = charArrayOf('b', 'e', ' ', 'y', 'o', 'u', 'r', ' ', 'o', 'w', 'n', ' ', 'l', 'a', 'n', 't', 'e', 'r', 'n'),
        val commonName: String = "johnsonlee.io",
        val organization: String = "Johnson Lee",
        val organizationalUnitName: String = "Engineering",
        val certOrganization: String = organization,
        val certOrganizationalUnitName: String = organizationalUnitName
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Authority

        if (alias != other.alias) return false
        if (!password.contentEquals(other.password)) return false
        if (commonName != other.commonName) return false
        if (organization != other.organization) return false
        if (organizationalUnitName != other.organizationalUnitName) return false
        if (certOrganization != other.certOrganization) return false
        if (certOrganizationalUnitName != other.certOrganizationalUnitName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = alias.hashCode()
        result = 31 * result + password.contentHashCode()
        result = 31 * result + commonName.hashCode()
        result = 31 * result + organization.hashCode()
        result = 31 * result + organizationalUnitName.hashCode()
        result = 31 * result + certOrganization.hashCode()
        result = 31 * result + certOrganizationalUnitName.hashCode()
        return result
    }

    fun aliasFile(suffix: String): File = File("${alias}${suffix}")

}
