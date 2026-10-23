package ph.edu.tip.goserve.domain;

public final class TermsDocument {
    public final String version, content, contentDigest, publishedAt;
    public TermsDocument(String version, String content, String contentDigest, String publishedAt) {
        this.version = version; this.content = content; this.contentDigest = contentDigest; this.publishedAt = publishedAt;
    }
}
