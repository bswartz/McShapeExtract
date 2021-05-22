# McShapeExtract

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## Usage
```
// First download MC server jar and deobfuscation mappings
wget -O minecraft-server-1.16.5.jar https://launcher.mojang.com/v1/objects/1b557e7b033b583cd9f66746b7a9ab1ec1673ced/server.jar
wget -O mappings-server-1.16.5.txt https://launcher.mojang.com/v1/objects/41285beda6d251d190f2bf33beadd4fee187df7a/server.txt

// Use Reconstruct to deobfuscate the jar
java -jar reconstruct-cli-1.3.2.jar -agree -threads 4 -jar minecraft-server-1.16.5.jar -mapping mappings-server-1.16.5.txt -output deobf-server-1.16.5.jar -exclude "com.google.,com.mojang.,io.netty.,it.unimi.dsi.fastutil.,javax.,joptsimple.,org.apache."

// Compile
javac -cp deobf-server-1.16.5.jar:javax.json-1.0.4.jar Extract.java

// Run
java -cp deobf-server-1.16.5.jar:javax.json-1.0.4.jar:. Extract
```

## Notes

The names in shapes.json are just strings I came up with. They have no special meaning, and aren't even
necessary to consume the output. I found that if I'm working with block shapes it's more friendly to
have human-readable names, though.

## References

Depends on Minecraft server 1.16.5 Download the jar and mappings based on links found
[here](https://launchermeta.mojang.com/mc/game/version_manifest.json).

Special thanks to [Reconstruct](https://github.com/LXGaming/Reconstruct) for providing a deobfuscator
that makes linking to the Minecraft jar easy.

Depends on [Glassfish JSON processor](https://javaee.github.io/jsonp/).

## License
McShapeExtract is licensed under the [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) license.
