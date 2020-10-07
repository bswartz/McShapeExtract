/*
 *  Copyright (c) 2020 Ben Swartzlander
 *  All rights reserved.
 */

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

public class Extract {

    static class Shape {

        final List<AABB> boxes;

        Shape(List<AABB> boxes) {

            this.boxes = boxes;
        }

        @Override
        public int hashCode() {

            int n = 0;
            for (AABB box : boxes) {
                n = 31 * n + box.hashCode() + (n >>> 24);
            }
            return n;
        }

        @Override
        public boolean equals(Object o) {

            if (this == o) {
                return true;
            } else if (null == o || ! (o instanceof Shape)) {
                return false;
            }
            Shape s = (Shape) o;
            final int n = boxes.size();
            if (s.boxes.size() != n) {
                return false;
            }
            for (int i = 0; i < n; i++) {
                if (! boxes.get(i).equals(s.boxes.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static TreeMap<String,Shape> shapesByName = new TreeMap<>();
    private static HashMap<Shape,String> shapeNameMap = new HashMap<>();
    private static HashMap<Shape,Integer> refCounts = new HashMap<>();
    private static HashMap<String,ArrayList<String>> shapeRefs = new HashMap<>();

    /*
     *  loadShapes reads the json file that associates shapes with names
     */
    private static void loadShapes() throws IOException {

        JsonArray jsonShapes;
        try (FileReader in = new FileReader(new File("shapes.json"))) {
            JsonReader reader = Json.createReader(in);
            jsonShapes = reader.readArray();
        }

        for (JsonObject jsonShape : jsonShapes.getValuesAs(JsonObject.class)) {
            String name = jsonShape.getString("name");
            ArrayList<AABB> boxes = new ArrayList<>();
            for (JsonArray box : jsonShape.getJsonArray("boxes").getValuesAs(JsonArray.class)) {
                final double[] n = new double[6];
                for (int i = 0; i < 6; i++) {
                    n[i] = box.getJsonNumber(i).doubleValue();
                }
                boxes.add(new AABB(n[0], n[1], n[2], n[3], n[4], n[5]));
            }
            Shape shape = new Shape(boxes);
            shapesByName.put(name, shape);
            shapeNameMap.put(shape, name);
            refCounts.put(shape, 0);
        }
    }

    /*
     *  refShape increments the refcount for a shape so we can sort the shapes by
     *  most-used, and also keeps a list of back references which will be dumped
     *  to the shapes file in case we encounter a new "unknown" shape.
     */
    private static void refShape(Shape shape, String refName) {

        String shapeName;
        Integer count = refCounts.get(shape);
        if (null == count) {
            shapeName = String.format("unknown%04d", shapesByName.size());
            shapesByName.put(shapeName, shape);
            shapeNameMap.put(shape, shapeName);
            refCounts.put(shape, 1);
        } else {
            shapeName = shapeNameMap.get(shape);
            refCounts.put(shape, count + 1);
        }
        ArrayList<String> refs = shapeRefs.get(shapeName);
        if (null == refs) {
            refs = new ArrayList<>();
            shapeRefs.put(shapeName, refs);
        }
        refs.add(refName);
    }

    private static final String[] SHAPE_TYPES = new String[] {"outline", "collision", "occlude", "visual", "blockSupport"};

    /*
     *  getShape extracts a shape from the Minecraft code for a given block
     *  state. 5 types of shapes are supported for each block state.
     */
    private static VoxelShape getShape(BlockState state, String shapeType) {

        switch (shapeType) {
        case "outline":
            return state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        case "collision":
            return state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        case "occlude":
            return state.getOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        case "visual":
            return state.getVisualShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
        case "blockSupport":
            return state.getBlockSupportShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        default:
            throw new AssertionError("invalid type: " + shapeType);
        }
    }

    /*
     *  extractData generates the extract.json file
     */
    private static void extractData() throws IOException {

        HashMap<String,HashMap<Integer,Shape>> shapeMap = new HashMap<>();
        for (String shapeType : SHAPE_TYPES) {
            shapeMap.put(shapeType, new HashMap<>());
        }

        PrintWriter out = new PrintWriter("extract.json");
        out.print("{\n \"blocks\":{");

        // Iterate of each block in the Minecraft registery and write its
        // states with their IDs and properties.
        int blockCount = 0;
        int maxState = -1;
        for (Block block : Registry.BLOCK) {
            if (0 < blockCount) out.print(',');
            blockCount++;
            String name = Registry.BLOCK.getKey(block).toString();
            out.printf("\n  \"%s\":{\n   \"keys\":[", name);
            ArrayList<Property<?>> blockProperties = new ArrayList<>();
            for (Property<?> prop : block.getStateDefinition().getProperties()) {
                if (0 < blockProperties.size()) out.print(',');
                out.printf("\"%s\"", prop.getName());
                blockProperties.add(prop);
            }
            out.print("],\n   \"states\":[");
            int stateCount = 0;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (0 < stateCount) out.print(',');
                stateCount++;
                int id = Block.getId(state);
                out.printf("\n    {\"id\":%d,\"values\":[", id);
                String refName = name.replace("minecraft:", "");
                int propCount = 0;
                for (Property<?> prop : blockProperties) {
                    if (0 < propCount) out.print(',');
                    propCount++;
                    String value = state.getValue(prop).toString();
                    out.printf("\"%s\"", value);
                    refName += String.format(",%s=%s", prop.getName(), value);
                }
                out.print("]}");

                for (String shapeType : SHAPE_TYPES) {
                    Shape shape = new Shape(getShape(state, shapeType).toAabbs());
                    refShape(shape, refName + "," + shapeType);
                    shapeMap.get(shapeType).put(id, shape);
                }

                if (id > maxState) {
                    maxState = id;
                }
            }
            out.print("\n   ]\n  }");
        }
        out.print("\n },\n");

        // Sort the shape names from most-used to least-used
        ArrayList<String> shapeNameList = new ArrayList<>();
        shapeNameList.addAll(shapesByName.keySet());
        Collections.sort(shapeNameList, (name1, name2) -> {

            int n = refCounts.get(shapesByName.get(name2)) - refCounts.get(shapesByName.get(name1));
            if (0 != n) {
                return n;
            }
            return name1.compareTo(name2);
        });

        // Write the shapes to the output. This array will be indexed by the
        // numbers in the following sections.
        HashMap<String,Integer> nameIndexMap = new HashMap<>();
        out.print(" \"shapes\":[");
        for (int i = 0; i < shapeNameList.size(); i++) {
            if (0 < i) out.print(',');
            String name = shapeNameList.get(i);
            nameIndexMap.put(name, i);
            out.printf("\n  {\n   \"name\":\"%s\",\n   \"boxes\":[", name);
            Shape shape = shapesByName.get(name);
            int boxCount = 0;
            for (AABB box : shape.boxes) {
                if (0 < boxCount) out.print(',');
                boxCount++;
                out.printf("[%s,%s,%s,%s,%s,%s]",
                        box.minX, box.minY, box.minZ,
                        box.maxX, box.maxY, box.maxZ);
            }
            out.print("]\n  }");
        }
        out.print("\n ]");

        // Write the shape number for every block state, for each of the
        // 5 shape types. These arrays are indexed by block state ID, and
        // the values are indicies into the shapes array.
        for (String shapeType : SHAPE_TYPES) {
            HashMap<Integer,Shape> map = shapeMap.get(shapeType);
            out.printf(",\n \"%ss\":[", shapeType);
            for (int i = 0; i <= maxState; i++) {
                if (0 < i) out.print(',');
                if (0 == i % 64) out.print("\n  ");
                Shape shape = map.get(i);
                String name = shapeNameMap.get(shape);
                int index = nameIndexMap.get(name);
                out.printf("%d", index);
            }
            out.print("\n ]");
        }

        out.print("\n}\n");
        out.close();
    }

    /*
     *  writeShapes updates the shapes.json file, sorting it by
     *  shape name, adding any new unknown shapes that were discovered
     *  and updating the ref counts. For unknown shapes, also include
     *  a list of back references to help decide on the shape name.
     */
    private static void writeShapes() throws IOException {

        PrintWriter out = new PrintWriter("shapes.json");
        out.print("[");
        int nameCount = 0;
        for (String name : shapesByName.keySet()) {
            if (0 < nameCount) out.print(',');
            nameCount++;
            out.printf("\n {\n  \"name\":\"%s\",\n  \"boxes\":[", name);
            Shape shape = shapesByName.get(name);
            int boxCount = 0;
            for (AABB box : shape.boxes) {
                if (0 < boxCount) out.print(',');
                boxCount++;
                out.printf("[%s,%s,%s,%s,%s,%s]",
                        box.minX, box.minY, box.minZ,
                        box.maxX, box.maxY, box.maxZ);
            }
            int refCount = refCounts.get(shape);
            out.printf("],\n  \"uses\":%d", refCount);
            if (name.startsWith("unknown")) {
                out.print(",\n  \"refs\":[");
                boolean comma = false;
                for (String ref : shapeRefs.get(name)) {
                    if (comma) out.print(',');
                    comma = true;
                    out.printf("\n   \"%s\"", ref);
                }
                out.print("\n  ]");
            }
            out.print("\n }");
        }
        out.print("\n]\n");
        out.close();
    }

    /*
     *  main
     */
    public static void main(String[] args) throws IOException {

        loadShapes();
        extractData();
        writeShapes();
    }
}
