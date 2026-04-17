package com.tixypt.chatting.support.ai.config;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.ai.document.Document;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

// RAG에 사용할 로컬 문서를 읽어서 Spring AI Document로 바꾼다
// 문서 로딩이랑 벡터를 쌓는 책임을 분리해 두고 나중에 다른 문서로 바뀌어도 이것만 교체하거나 확장하면 됨
@Component
@RequiredArgsConstructor
public class SupportAiDocumentLoader {

    private static final String TITLE_PREFIX = "# ";

    private final SupportAiProperties supportAiProperties;
    private volatile List<Document> cachedDocuments;

    // classpath 문서를 읽어서 Document 목록으로 반환
    public List<Document> load() {
        List<Document> loadedDocuments = cachedDocuments;
        if (loadedDocuments != null) {
            return loadedDocuments;
        }

        synchronized (this) {
            if (cachedDocuments == null) {
                cachedDocuments = loadDocuments();
            }
            return cachedDocuments;
        }
    }

    public int count() {
        return load().size();
    }

    // 실제 리소스 탐색이랑 Document 변환
    private List<Document> loadDocuments() {
        return Arrays.stream(resolveResources())
                .filter(Resource::exists)
                .map(this::toDocument)
                .toList();
    }

    // 프로퍼티에 지정된 패턴으로 classpath 리소스를 찾음
    private Resource[] resolveResources() {
        try {
            return new PathMatchingResourcePatternResolver()
                    .getResources(supportAiProperties.getRagResourcePattern());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    // Resource 하나를 Spring ai Document로 바꾼다
    // sourceType, sourceId, title 메타데이터를 같이 넣어 두면 나중에 어떤 문서가 검색에 걸렸는지 추적하기 쉬워짐
    private Document toDocument(Resource resource) {
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("sourceType", "RAG_DOC");
            metadata.put("sourceId", resource.getFilename());
            metadata.put("title", extractTitle(resource.getFilename(), content));
            return new Document(content, metadata);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    // 마크다운 첫 제목을 문서 표시용 title로 사용하고 제목이 없으면 파일명을 대체값으로 사용
    private String extractTitle(String filename, String content) {
        return content.lines()
                .map(String::trim)
                .filter(line -> line.startsWith(TITLE_PREFIX))
                .map(line -> line.substring(TITLE_PREFIX.length()).trim())
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(filename);
    }
}
