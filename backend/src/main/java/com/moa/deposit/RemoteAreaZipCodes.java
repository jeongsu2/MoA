package com.moa.deposit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

// 도서산간 판별 (D-25, 05 문서 Q-1 B). 주소 문자열이 아니라 우편번호 범위로 본다.
// 목록은 시작할 때 한 번 메모리에 올린다. 파일이 틀리면 서버가 뜨지 않게 해서 잘못된 배송비로 받지 않는다
@Component
public class RemoteAreaZipCodes {

    public static final String FILE = "remote-area-zip-codes.csv";
    private static final Pattern ZIP_CODE = Pattern.compile("\\d{5}");

    private final List<ZipRange> ranges;

    public RemoteAreaZipCodes() {
        this(readLines(FILE));
    }

    RemoteAreaZipCodes(List<String> lines) {
        this.ranges = parse(lines);
    }

    // 앞자리 0이 사라지지 않게 숫자로 바꾸지 않는다. 길이가 5로 같아서 문자열 비교가 숫자 비교와 같다
    public boolean isRemote(String zipCode) {
        if (zipCode == null || !ZIP_CODE.matcher(zipCode).matches()) {
            return false;
        }
        for (ZipRange range : ranges) {
            if (range.contains(zipCode)) {
                return true;
            }
        }
        return false;
    }

    private static List<ZipRange> parse(List<String> lines) {
        List<ZipRange> ranges = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length != 2) {
                throw new IllegalStateException("도서산간 목록 줄 형식이 다름: " + line);
            }
            String start = parts[0].strip();
            String end = parts[1].strip();
            if (!ZIP_CODE.matcher(start).matches() || !ZIP_CODE.matcher(end).matches() || start.compareTo(end) > 0) {
                throw new IllegalStateException("도서산간 목록 범위가 틀림: " + line);
            }
            ranges.add(new ZipRange(start, end));
        }
        return List.copyOf(ranges);
    }

    private static List<String> readLines(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("도서산간 목록을 읽지 못함: " + path, e);
        }
    }

    private record ZipRange(String start, String end) {

        boolean contains(String zipCode) {
            return start.compareTo(zipCode) <= 0 && zipCode.compareTo(end) <= 0;
        }
    }
}
