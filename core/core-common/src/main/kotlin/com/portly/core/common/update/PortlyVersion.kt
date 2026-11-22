package com.portly.core.common.update

/**
 * High-precision, zero-allocation version parser and comparator for Portly.
 *
 * Fully supports:
 * 1. Calendar Versioning (CalVer): `YYYY.MM.PATCH` (e.g. `2026.11.0`, `2026.11.1`, `2026.12.0`, `2027.01.0`).
 * 2. Pre-release Qualifiers: `-alpha.N`, `-beta.N`, `-rc.N` (e.g. `2026.11.0-beta.1`, `2026.11.0-rc.1`).
 * 3. Legacy Semantic Versioning (SemVer): `v1.0.0`, `1.0.1`, `2.0.0`.
 *
 * Critical Migration Properties:
 * - Any CalVer version (Year >= 2020) is strictly greater than any legacy SemVer version (Major < 2020).
 *   Example: `2026.11.0 > 1.0.0`, `2026.11.0 > 2.0.0`.
 * - Lexicographical comparison pitfalls are prevented: `2026.11.10 > 2026.11.2`.
 * - Pre-release hierarchy: `STABLE (final) > RC > BETA > ALPHA`.
 * - Computes monotonic 32-bit Android `versionCode`: `YYYY * 100000 + MM * 1000 + PATCH`.
 */
data class PortlyVersion(
    val raw: String,
    val isCalVer: Boolean,
    val yearOrMajor: Int,
    val monthOrMinor: Int,
    val patch: Int,
    val preReleaseType: PreReleaseType = PreReleaseType.STABLE,
    val preReleaseNumber: Int = 0,
) : Comparable<PortlyVersion> {

    enum class PreReleaseType(val priority: Int) {
        ALPHA(1),
        BETA(2),
        RC(3),
        STABLE(4), // Final production release (no qualifier)
    }

    val isPreRelease: Boolean
        get() = preReleaseType != PreReleaseType.STABLE

    /**
     * Standard monotonic Android versionCode:
     * - For CalVer (YYYY >= 2020): (YYYY * 100000) + (MM * 1000) + PATCH
     *   Example: 2026.11.0 -> 202611000, 2026.11.1 -> 202611001, 2026.12.0 -> 202612000
     * - For legacy SemVer (Major < 2020): (Major * 1000000) + (Minor * 1000) + Patch
     *   Example: 1.0.0 -> 1000000 (or build 1)
     */
    val estimatedVersionCode: Long
        get() = if (isCalVer) {
            (yearOrMajor.toLong() * 100000L) + (monthOrMinor.toLong() * 1000L) + patch.toLong()
        } else {
            (yearOrMajor.toLong() * 1000000L) + (monthOrMinor.toLong() * 1000L) + patch.toLong()
        }

    val formattedDisplayVersion: String
        get() = if (isCalVer) {
            buildString {
                append(yearOrMajor)
                append(".")
                append("%02d".format(monthOrMinor))
                append(".")
                append(patch)
                if (isPreRelease) {
                    append("-")
                    append(preReleaseType.name.lowercase())
                    if (preReleaseNumber > 0) {
                        append(".")
                        append(preReleaseNumber)
                    }
                }
            }
        } else {
            buildString {
                append(yearOrMajor)
                append(".")
                append(monthOrMinor)
                append(".")
                append(patch)
                if (isPreRelease) {
                    append("-")
                    append(preReleaseType.name.lowercase())
                    if (preReleaseNumber > 0) {
                        append(".")
                        append(preReleaseNumber)
                    }
                }
            }
        }

    override fun compareTo(other: PortlyVersion): Int {
        // 1. CalVer (Year >= 2020) is always newer than legacy SemVer (Major < 2020)
        if (this.isCalVer && !other.isCalVer) return 1
        if (!this.isCalVer && other.isCalVer) return -1

        // 2. Compare Year (CalVer) or Major (SemVer)
        if (this.yearOrMajor != other.yearOrMajor) {
            return this.yearOrMajor.compareTo(other.yearOrMajor)
        }

        // 3. Compare Month (CalVer) or Minor (SemVer)
        if (this.monthOrMinor != other.monthOrMinor) {
            return this.monthOrMinor.compareTo(other.monthOrMinor)
        }

        // 4. Compare Patch
        if (this.patch != other.patch) {
            return this.patch.compareTo(other.patch)
        }

        // 5. Compare Pre-Release Type (ALPHA < BETA < RC < STABLE)
        if (this.preReleaseType != other.preReleaseType) {
            return this.preReleaseType.priority.compareTo(other.preReleaseType.priority)
        }

        // 6. Compare Pre-Release Sequential Number (e.g. beta.2 > beta.1)
        return this.preReleaseNumber.compareTo(other.preReleaseNumber)
    }

    companion object {
        private val CALVER_REGEX = Regex("""^v?(\d{4})\.(\d{1,2})\.(\d+)(?:-([a-zA-Z0-9.]+))?$""", RegexOption.IGNORE_CASE)
        private val SEMVER_REGEX = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([a-zA-Z0-9.]+))?$""", RegexOption.IGNORE_CASE)

        /**
         * Parses any version string safely without throwing exceptions.
         * Returns null if the format cannot be parsed into a valid version.
         */
        fun parse(versionStr: String?): PortlyVersion? {
            if (versionStr.isNullOrBlank()) return null
            val clean = versionStr.trim()

            // 1. Check CalVer match (YYYY.MM.PATCH)
            val calVerMatch = CALVER_REGEX.matchEntire(clean)
            if (calVerMatch != null) {
                val year = calVerMatch.groupValues[1].toIntOrNull() ?: return null
                val month = calVerMatch.groupValues[2].toIntOrNull() ?: return null
                val patch = calVerMatch.groupValues[3].toIntOrNull() ?: return null
                val qualifier = calVerMatch.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }

                val (preType, preNum) = parseQualifier(qualifier)

                return PortlyVersion(
                    raw = clean,
                    isCalVer = true,
                    yearOrMajor = year,
                    monthOrMinor = month,
                    patch = patch,
                    preReleaseType = preType,
                    preReleaseNumber = preNum,
                )
            }

            // 2. Check SemVer match (MAJOR.MINOR.PATCH)
            val semVerMatch = SEMVER_REGEX.matchEntire(clean)
            if (semVerMatch != null) {
                val major = semVerMatch.groupValues[1].toIntOrNull() ?: return null
                val minor = semVerMatch.groupValues[2].toIntOrNull() ?: return null
                val patch = semVerMatch.groupValues[3].toIntOrNull() ?: return null
                val qualifier = semVerMatch.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }

                val isCalVer = major >= 2020
                val (preType, preNum) = parseQualifier(qualifier)

                return PortlyVersion(
                    raw = clean,
                    isCalVer = isCalVer,
                    yearOrMajor = major,
                    monthOrMinor = minor,
                    patch = patch,
                    preReleaseType = preType,
                    preReleaseNumber = preNum,
                )
            }

            // 3. Fallback for single integer or partial strings
            val stripped = clean.removePrefix("v").removePrefix("V")
            val baseParts = stripped.split("-", limit = 2)
            val segments = baseParts[0].split(".").mapNotNull { it.toIntOrNull() }
            if (segments.isEmpty()) return null

            val major = segments[0]
            val minor = segments.getOrElse(1) { 0 }
            val patch = segments.getOrElse(2) { 0 }
            val (preType, preNum) = parseQualifier(baseParts.getOrNull(1))

            return PortlyVersion(
                raw = clean,
                isCalVer = major >= 2020,
                yearOrMajor = major,
                monthOrMinor = minor,
                patch = patch,
                preReleaseType = preType,
                preReleaseNumber = preNum,
            )
        }

        private fun parseQualifier(qualifier: String?): Pair<PreReleaseType, Int> {
            if (qualifier.isNullOrBlank()) return Pair(PreReleaseType.STABLE, 0)
            val q = qualifier.lowercase()
            val num = q.filter { it.isDigit() }.toIntOrNull() ?: 1

            return when {
                q.contains("alpha") -> Pair(PreReleaseType.ALPHA, num)
                q.contains("beta") -> Pair(PreReleaseType.BETA, num)
                q.contains("rc") -> Pair(PreReleaseType.RC, num)
                else -> Pair(PreReleaseType.BETA, num)
            }
        }

        /**
         * Authoritative upgrade eligibility check:
         * Verifies both semantic CalVer/SemVer comparison and monotonic Android versionCode.
         */
        fun isUpgradeEligible(
            candidateVersionName: String,
            candidateVersionCode: Long,
            candidateIsPrerelease: Boolean,
            channel: com.portly.core.model.ReleaseChannel,
            currentVersionName: String,
            currentVersionCode: Long,
        ): Boolean {
            // 1. Stable channel strictly excludes any pre-release (alpha, beta, rc)
            if (channel == com.portly.core.model.ReleaseChannel.STABLE && candidateIsPrerelease) {
                return false
            }

            // 2. Parse version strings
            val candidateVer = parse(candidateVersionName)
            val currentVer = parse(currentVersionName)

            // If parsed, check pre-release on the version object as well
            if (channel == com.portly.core.model.ReleaseChannel.STABLE && candidateVer?.isPreRelease == true) {
                return false
            }

            // 3. Monotonic Android versionCode check (Anti-downgrade)
            val effectiveCandidateCode = if (candidateVersionCode > 0) {
                candidateVersionCode
            } else {
                candidateVer?.estimatedVersionCode ?: 0L
            }

            if (effectiveCandidateCode <= currentVersionCode) {
                return false // Downgrade or same build rejected
            }

            // 4. Semantic version comparison
            if (candidateVer != null && currentVer != null) {
                if (candidateVer <= currentVer) {
                    return false
                }
            }

            return true
        }
    }
}
