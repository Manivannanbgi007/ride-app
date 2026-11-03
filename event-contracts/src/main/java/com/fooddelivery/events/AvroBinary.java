package com.fooddelivery.events;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

/**
 * Plain Avro binary encoding of an event, used by the transactional outbox: the event is stored in
 * the database (bytes + the schema it was written with) and published to Kafka later.
 * <p>
 * Keeping the writer schema next to the bytes means a stored event can always be read back, even
 * after the event class has moved on to a newer schema version.
 */
public final class AvroBinary {

    private AvroBinary() {
    }

    /** Encodes the event with its own schema (the schema to store next to the bytes). */
    public static byte[] encode(SpecificRecord event) {
        SpecificDatumWriter<SpecificRecord> writer =
                new SpecificDatumWriter<>(event.getSchema(), SpecificData.getForClass(event.getClass()));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(bytes, null);
        try {
            writer.write(event, encoder);
            encoder.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not encode " + event.getSchema().getFullName(), e);
        }
        return bytes.toByteArray();
    }

    /**
     * Reads stored bytes as a generic record with the schema they were written with. The Kafka Avro
     * serializer sends it with exactly that schema, so consumers see the event as it was produced.
     */
    public static GenericRecord decode(byte[] bytes, Schema writerSchema) {
        GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(writerSchema);
        try {
            return reader.read(null, DecoderFactory.get().binaryDecoder(bytes, null));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not decode " + writerSchema.getFullName(), e);
        }
    }
}
