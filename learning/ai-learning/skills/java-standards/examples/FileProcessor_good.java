import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// CORRECT VOORBEELD — elke keuze volgt de java-standards skill
// Vergelijk met FileProcessor_bad.java

public class FileProcessor_good {

    private static final Logger log = LoggerFactory.getLogger(FileProcessor_good.class);

    // ✓ R3: record = immutable data carrier, geen setter, geen null-velden
    record Person(String name, int age) {
        // ✓ R7: fail-fast validatie in canonical constructor
        Person {
            Objects.requireNonNull(name, "name must not be null");
            if (age < 0) throw new IllegalArgumentException("age must be >= 0, got: " + age);
        }
    }

    // ✓ R4: geen magic numbers/strings als literal in code
    private static final int CSV_COLUMN_NAME = 0;
    private static final int CSV_COLUMN_AGE  = 1;
    private static final int CSV_MIN_COLUMNS = 2;

    // ✓ R5: naam vertelt precies wat de methode doet
    // ✓ R6: Optional i.p.v. null-return
    // ✓ R9: methode past op één scherm
    public Optional<List<Person>> parseCsvFile(Path csvPath) {
        // ✓ R7: precondition check bovenaan
        Objects.requireNonNull(csvPath, "csvPath must not be null");

        try {
            List<Person> people = Files.lines(csvPath)
                    .skip(1)                          // header overslaan
                    .filter(line -> !line.isBlank())
                    .map(this::parsePersonLine)       // ✓ R9: apart gehouden
                    .toList();

            // ✓ R8: SLF4J, structured, wat + count — niet HOE
            log.info("CSV parsed successfully. path={} rows={}", csvPath, people.size());
            return Optional.of(people);

        } catch (IOException e) {
            // ✓ R1: originele exception bewaard als cause
            // ✓ R2: vangen wat we verwachten (IOException), niet alles
            throw new UncheckedIOException("Failed to read CSV: " + csvPath, e);
        }
    }

    // ✓ R9: extractie maakt parseCsvFile kort én testbaar in isolatie
    private Person parsePersonLine(String rawLine) {
        String[] columns = rawLine.split(",", -1);
        if (columns.length < CSV_MIN_COLUMNS) {
            // ✓ R1: duidelijke fout met context — nooit stil negeren
            throw new IllegalArgumentException(
                    "CSV line has fewer than " + CSV_MIN_COLUMNS + " columns: [" + rawLine + "]");
        }
        String name = columns[CSV_COLUMN_NAME].strip();
        int age;
        try {
            age = Integer.parseInt(columns[CSV_COLUMN_AGE].strip());
        } catch (NumberFormatException e) {
            // ✓ R2: specifieke exception, ✓ R1: cause behouden
            throw new IllegalArgumentException(
                    "Invalid age value in CSV line: [" + rawLine + "]", e);
        }
        return new Person(name, age);  // ✓ R3: record, validatie in constructor
    }

    // ✓ R10: main doet alleen bootstrap — logica zit in parseCsvFile
    public static void main(String[] args) {
        var processor = new FileProcessor_good();
        processor.parseCsvFile(Path.of("people.csv"))
                 .ifPresentOrElse(
                         people -> log.info("Loaded {} people", people.size()),
                         ()     -> log.warn("CSV was empty or not found"));
    }
}
