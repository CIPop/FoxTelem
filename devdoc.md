### How to build using Ant

#### Prerequites

Clone & build https://github.com/ac2cz/predict4java using Maven:

`mvn package [-DskipTests]`

Copy the jar from `target` to `FoxTelem/lib`.

#### Build

`ant jar`

#### Install

Unpack the latest package and replace the `FoxTelem.jar` file with the one from `build/jar`.
