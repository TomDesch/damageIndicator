"""Check the deployable JAR, not just Maven's test classpath."""

from pathlib import Path
import re
import struct
import zipfile
import xml.etree.ElementTree as ET

PLUGIN_PACKAGE = "io/github/stealingdapenta/damageindicator/"
JAVA_21_CLASS_VERSION = 65
FORBIDDEN_PACKAGES = (
    "org/bukkit/",
    "net/minecraft/",
    "org/mockito/",
    "net/bytebuddy/",
    "org/objenesis/",
    "org/junit/",
    "com/google/",
    "net/kyori/",
    "org/jetbrains/",
    "org/intellij/",
    "org/jspecify/",
)
PROJECT_LICENSE = "META-INF/LICENSE"
PROJECT_POM = "META-INF/maven/io.github.stealingdapenta/damageindicator/pom.xml"


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


artifacts = [
    path for path in Path("target").glob("damageindicator-*.jar")
    if not path.stem.endswith(("-sources", "-javadoc", "-tests"))
]
require(len(artifacts) == 1, f"Expected one shaded plugin JAR, found {artifacts}")
artifact = artifacts[0]

with zipfile.ZipFile(artifact) as jar:
    entries = jar.namelist()
    require(PROJECT_POM in entries, "Embedded project metadata is missing")
    metadata_pom = ET.fromstring(jar.read(PROJECT_POM))
    properties = {
        child.tag.rsplit("}", 1)[-1]: child.text
        for child in metadata_pom.find("{http://maven.apache.org/POM/4.0.0}properties")
    }
    adventure_version = properties["adventure.version"]
    annotations_version = properties["annotations.version"]
    jspecify_version = properties["jspecify.version"]
    adventure_license = f"META-INF/licenses/adventure-{adventure_version}-LICENSE.txt"
    apache_licenses = (
        f"META-INF/licenses/jetbrains-annotations-{annotations_version}-LICENSE.txt",
        f"META-INF/licenses/jspecify-{jspecify_version}-LICENSE.txt",
    )
    required_resources = (
        "plugin.yml",
        "config.yml",
        PROJECT_LICENSE,
        "META-INF/THIRD_PARTY_NOTICES.txt",
        adventure_license,
        *apache_licenses,
        f"META-INF/licenses/jspecify-{jspecify_version}-AUTHORS.txt",
    )
    for resource in required_resources:
        require(resource in entries, f"Missing required resource: {resource}")
    for entry in entries:
        if not entry.endswith(".class"):
            continue
        require(not entry.startswith(FORBIDDEN_PACKAGES), f"Unintended bundled class: {entry}")
        require(not entry.endswith("module-info.class"), f"Dependency module descriptor survived shading: {entry}")
        if entry.startswith(PLUGIN_PACKAGE) and "/internal/" not in entry:
            version = struct.unpack(">H", jar.read(entry)[6:8])[0]
            require(version == JAVA_21_CLASS_VERSION, f"Unexpected bytecode version {version}: {entry}")
    metadata = jar.read("plugin.yml").decode("utf-8")
    require(re.search(r"(?m)^api-version:\s*['\"]?26\.2['\"]?\s*$", metadata),
            "Minecraft 26.2 minimum changed without an explicit compatibility decision")
    require("permission notice shall be included" in jar.read(PROJECT_LICENSE).decode("utf-8"),
            "Project MIT license is incomplete")
    require("KyoriPowered" in jar.read(adventure_license).decode("utf-8"),
            "Upstream Adventure attribution is missing")
    for resource in apache_licenses:
        require("END OF TERMS AND CONDITIONS" in jar.read(resource).decode("utf-8"),
                f"Apache-2.0 terms are incomplete: {resource}")
    require(any(entry.startswith(PLUGIN_PACKAGE + "internal/kyori/") for entry in entries),
            "Adventure implementation is missing from the shared artifact")
    notices = jar.read("META-INF/THIRD_PARTY_NOTICES.txt").decode("utf-8")
    for coordinate in (
        f"net.kyori:adventure-api:{adventure_version}",
        f"net.kyori:adventure-key:{adventure_version}",
        f"net.kyori:adventure-text-serializer-legacy:{adventure_version}",
        f"org.jetbrains:annotations:{annotations_version}",
        f"org.jspecify:jspecify:{jspecify_version}",
    ):
        require(coordinate in notices, f"Bundled dependency attribution is stale: {coordinate}")

print(f"Verified shaded artifact: {artifact} ({artifact.stat().st_size:,} bytes)")
