import java.io.*;
import java.util.*;

// ANTI-PATROON VOORBEELD — elke regel met ❌ markeert een schending
// Zie FileProcessor_good.java voor de correcte versie

public class FileProcessor_bad {

    // ❌ R3: geen final, geen record — mutable data carrier
    static class Person {
        String name;
        int age;
    }

    // ❌ R5: naam zegt niets over wat de methode doet
    // ❌ R9: method is > 20 regels, doet te veel
    public List<Person> proc(String f) {
        // ❌ R4: magic string, magic number
        List<Person> l = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(f));
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; }  // skip header
                // ❌ R5: s, p zijn betekenisloos
                String[] s = line.split(",");
                Person p = new Person();
                p.name = s[0];
                // ❌ R1/R2: NumberFormatException wordt stil genegeerd
                try {
                    p.age = Integer.parseInt(s[1].trim());
                } catch (Exception e) {}   // ❌ R1: swallowed + R2: te breed
                l.add(p);
            }
            br.close();
        } catch (Exception e) {            // ❌ R2: catch alles
            // ❌ R1: printStackTrace ≠ logging
            // ❌ R8: zou SLF4J moeten zijn
            e.printStackTrace();
        }
        // ❌ R6: null return in plaats van Optional
        return l.isEmpty() ? null : l;
    }

    public static void main(String[] args) {
        FileProcessor_bad fp = new FileProcessor_bad();
        List<Person> result = fp.proc("people.csv");
        // ❌ R8: System.out in productie-code
        System.out.println("Resultaat: " + result);
    }
}
