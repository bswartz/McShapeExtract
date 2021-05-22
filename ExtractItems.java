/*
 *  Copyright (c) 2021 Ben Swartzlander
 *  All rights reserved.
 */

import net.minecraft.core.Registry;
import net.minecraft.world.item.Item;

import java.io.FileNotFoundException;
import java.io.PrintWriter;

public class ExtractItems {

    /*
     *  extractData generates the items.json file
     */
    private static void extractData() throws FileNotFoundException {

        PrintWriter out = new PrintWriter("items.json");
        out.print("{\n \"items\":{");

        int count = 0;
        for (Item item : Registry.ITEM) {
            if (0 < count) out.print(',');
            count++;
            String name = Registry.ITEM.getKey(item).toString();
            out.printf("\n  \"%s\":{", name);
            out.printf("\n   \"stack\": %d,", item.getMaxStackSize());
            out.printf("\n   \"durability\": %d", item.getMaxDamage());
            out.printf("\n  }");
        }

        out.print("\n }\n}\n");
        out.close();
    }

    public static void main(String[] args) throws FileNotFoundException {

        extractData();
    }
}
