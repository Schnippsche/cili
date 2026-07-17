import csv
import uuid
from datetime import datetime

input_file = "zusammengefuhrt.csv"
output_file = "zusammengefuhrt_processed.csv"

with open(input_file, newline='', encoding='utf-8') as infile, \
     open(output_file, 'w', newline='', encoding='utf-8') as outfile:

    reader = csv.DictReader(infile)
    fieldnames = reader.fieldnames + ['uuid']
    writer = csv.DictWriter(outfile, fieldnames=fieldnames)
    writer.writeheader()

    for row in reader:
        # Datum umwandeln: ISO-8601 -> MySQL DATE (YYYY-MM-DD)
        datum_raw = row.get('datum', '').strip()
        if datum_raw:
            try:
                dt = datetime.fromisoformat(datum_raw)
                row['datum'] = dt.strftime('%Y-%m-%d')
            except ValueError:
                pass  # Datum unverändert lassen falls Format unbekannt

        # UUID erzeugen wenn bilder-Spalte nicht leer
        bilder = row.get('bilder', '').strip()
        row['uuid'] = str(uuid.uuid4()) if bilder else ''

        writer.writerow(row)

print("Fertig! Ausgabe: " + output_file)
