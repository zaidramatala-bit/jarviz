/*
* Copyright 2020 Expedia, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.vrbo.jarviz.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vrbo.jarviz.model.CouplingRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import static com.vrbo.jarviz.util.JsonUtils.fromJsonString;

public class CouplingRecordWriterTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testWriteAsJson_SingleRecord() throws IOException {
        final File file = newOutputFile();
        final CouplingRecord record = newRecord("MySource", "methodA", "MyTarget", "methodB");

        final CouplingRecordWriter writer = new CouplingRecordWriter(file.getPath());
        writer.writeAsJson(record);
        assertThat(writer.close()).isTrue();

        assertThat(readLines(file)).hasSize(1);
        assertThat(fromJsonString(readLines(file).get(0), CouplingRecord.class)).isEqualTo(record);
    }

    @Test
    public void testWriteAsJson_MultipleRecordsArePreservedAsNewlineDelimitedJson() throws IOException {
        final File file = newOutputFile();

        final CouplingRecordWriter writer = new CouplingRecordWriter(file.getPath());
        for (int i = 0; i < 3; i++) {
            writer.writeAsJson(newRecord("MySource", "method" + i, "MyTarget", "target" + i));
        }
        writer.close();

        final List<String> lines = readLines(file);
        assertThat(lines).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(fromJsonString(lines.get(i), CouplingRecord.class).getSourceMethod()).isEqualTo("method" + i);
        }

        // Every record must be terminated by a newline, otherwise a streaming jsonl reader
        // would silently drop the last record.
        assertThat(readFile(file)).endsWith("\n");
    }

    @Test
    public void testWriteAsJson_EmptyAppSetNameIsOmittedFromTheJson() throws IOException {
        final File file = newOutputFile();

        final CouplingRecordWriter writer = new CouplingRecordWriter(file.getPath());
        writer.writeAsJson(new CouplingRecord.Builder()
                               .appSetName("")
                               .applicationName("myApp")
                               .artifactFileName("my-app-1.0.0.jar")
                               .artifactId("my-app")
                               .artifactGroup("com.foo.bar")
                               .artifactVersion("1.0.0")
                               .sourceClass("com.foo.MySource")
                               .sourceMethod("methodA")
                               .targetClass("com.foo.MyTarget")
                               .targetMethod("methodB")
                               .build());
        writer.close();

        final String json = readLines(file).get(0);
        assertThat(json).doesNotContain("appSetName");
        assertThat(json).contains("\"applicationName\":\"myApp\"");
    }

    @Test
    public void testWriteAsJson_SpecialCharactersAreEscaped() throws IOException {
        final File file = newOutputFile();

        final CouplingRecordWriter writer = new CouplingRecordWriter(file.getPath());
        writer.writeAsJson(newRecord("My\"Quoted\"Source", "method\nWithNewLine", "My\\Target", "method\tTab"));
        writer.close();

        // A raw newline inside a field would break the one-record-per-line contract.
        final List<String> lines = readLines(file);
        assertThat(lines).hasSize(1);
        final CouplingRecord parsed = fromJsonString(lines.get(0), CouplingRecord.class);
        assertThat(parsed.getSourceClass()).isEqualTo("My\"Quoted\"Source");
        assertThat(parsed.getSourceMethod()).isEqualTo("method\nWithNewLine");
        assertThat(parsed.getTargetClass()).isEqualTo("My\\Target");
        assertThat(parsed.getTargetMethod()).isEqualTo("method\tTab");
    }

    @Test
    public void testWriteAsJson_NonAsciiCharactersAreWrittenAsUtf8() throws IOException {
        final File file = newOutputFile();

        final CouplingRecordWriter writer = new CouplingRecordWriter(file.getPath());
        writer.writeAsJson(newRecord("com.foo.Ünïcødé", "méthodÀ", "com.foo.日本語", "メソッド"));
        writer.close();

        final CouplingRecord parsed = fromJsonString(readLines(file).get(0), CouplingRecord.class);
        assertThat(parsed.getSourceClass()).isEqualTo("com.foo.Ünïcødé");
        assertThat(parsed.getTargetClass()).isEqualTo("com.foo.日本語");
        assertThat(parsed.getTargetMethod()).isEqualTo("メソッド");
    }

    @Test
    public void testFileStreamIsOpenedLazily() {
        final File file = new File(tempFolder.getRoot(), "lazy.jsonl");
        final CouplingRecordWriter writer = new CouplingRecordWriter(file.getPath());

        assertThat(file).doesNotExist();

        writer.writeAsJson(newRecord("MySource", "methodA", "MyTarget", "methodB"));
        writer.close();

        assertThat(file).exists();
    }

    @Test
    public void testClose_WithoutAnyWriteReturnsFalse() {
        final File file = new File(tempFolder.getRoot(), "never-written.jsonl");
        final CouplingRecordWriter writer = new CouplingRecordWriter(file.getPath());

        assertThat(writer.close()).isFalse();
        assertThat(file).doesNotExist();
    }

    @Test
    public void testClose_IsIdempotent() throws IOException {
        final File file = newOutputFile();
        final CouplingRecordWriter writer = new CouplingRecordWriter(file.getPath());
        writer.writeAsJson(newRecord("MySource", "methodA", "MyTarget", "methodB"));

        assertThat(writer.close()).isTrue();
        assertThat(writer.close()).isTrue();
        assertThat(readLines(file)).hasSize(1);
    }

    /**
     * Documents the current behaviour: writing after {@code close()} is silently discarded, because
     * the underlying {@link java.io.PrintWriter} swallows the IO error instead of throwing.
     * This is asserted as-is, no production behaviour was changed.
     */
    @Test
    public void testWriteAsJson_AfterCloseIsSilentlyDiscarded() throws IOException {
        final File file = newOutputFile();
        final CouplingRecordWriter writer = new CouplingRecordWriter(file.getPath());
        writer.writeAsJson(newRecord("MySource", "methodA", "MyTarget", "methodB"));
        writer.close();

        writer.writeAsJson(newRecord("MySource", "lostMethod", "MyTarget", "methodB"));

        assertThat(readLines(file)).hasSize(1);
        assertThat(readFile(file)).doesNotContain("lostMethod");
    }

    @Test
    public void testWriteAsJson_FailsWhenParentDirectoryDoesNotExist() {
        final File file = new File(tempFolder.getRoot(), "no-such-dir" + File.separator + "out.jsonl");
        final CouplingRecordWriter writer = new CouplingRecordWriter(file.getPath());

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> writer.writeAsJson(newRecord("MySource", "methodA", "MyTarget", "methodB")))
            .withMessageContaining("Cannot write to file");

        assertThat(file).doesNotExist();
    }

    @Test
    public void testWriteAsJson_FailsWhenPathIsADirectory() throws IOException {
        final File dir = tempFolder.newFolder("output-dir");
        final CouplingRecordWriter writer = new CouplingRecordWriter(dir.getPath());

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> writer.writeAsJson(newRecord("MySource", "methodA", "MyTarget", "methodB")))
            .withMessageContaining("Cannot write to file");
    }

    /**
     * Documents the current behaviour: an existing report file is truncated (not appended to) by a new writer.
     */
    @Test
    public void testWriteAsJson_TruncatesAnExistingFile() throws IOException {
        final File file = newOutputFile();
        Files.write(file.toPath(), "stale content\n".getBytes(StandardCharsets.UTF_8));

        final CouplingRecordWriter writer = new CouplingRecordWriter(file.getPath());
        writer.writeAsJson(newRecord("MySource", "methodA", "MyTarget", "methodB"));
        writer.close();

        assertThat(readFile(file)).doesNotContain("stale content");
        assertThat(readLines(file)).hasSize(1);
    }

    private File newOutputFile() throws IOException {
        return tempFolder.newFile("couplings.jsonl");
    }

    private static CouplingRecord newRecord(final String sourceClass,
                                            final String sourceMethod,
                                            final String targetClass,
                                            final String targetMethod) {
        return new CouplingRecord.Builder()
                   .appSetName("myAppSet")
                   .applicationName("myApp")
                   .artifactFileName("my-app-1.0.0.jar")
                   .artifactId("my-app")
                   .artifactGroup("com.foo.bar")
                   .artifactVersion("1.0.0")
                   .sourceClass(sourceClass)
                   .sourceMethod(sourceMethod)
                   .targetClass(targetClass)
                   .targetMethod(targetMethod)
                   .build();
    }

    private static String readFile(final File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static List<String> readLines(final File file) throws IOException {
        return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
    }
}
