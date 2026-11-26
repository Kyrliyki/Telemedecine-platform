plugins {
    id("com.diffplug.spotless")
    id("com.github.spotbugs")
}

spotless {
    isEnforceCheck = false
    java {
        target("src/**/*.java")
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target("/*.md", ".gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

spotbugs {
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
    ignoreFailures.set(true)
}
