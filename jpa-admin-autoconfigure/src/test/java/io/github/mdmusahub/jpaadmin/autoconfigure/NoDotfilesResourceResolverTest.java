package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceResolverChain;

/** Hidden files and folders are refused by the console's asset handler, and everything else passes through. */
class NoDotfilesResourceResolverTest {

    /** A chain that records how often it is asked, so a test can see that a refused path never reaches it. */
    private static final class RecordingChain implements ResourceResolverChain {

        private final Resource answer = new ByteArrayResource(new byte[0]);
        private final List<String> asked = new ArrayList<>();

        @Override
        public Resource resolveResource(HttpServletRequest request, String requestPath,
                List<? extends Resource> locations) {
            asked.add(requestPath);
            return answer;
        }

        @Override
        public String resolveUrlPath(String resourcePath, List<? extends Resource> locations) {
            asked.add(resourcePath);
            return resourcePath;
        }
    }

    private final NoDotfilesResourceResolver resolver = new NoDotfilesResourceResolver();

    @ParameterizedTest
    @ValueSource(strings = {
            ".gitkeep", ".DS_Store", ".env", ".htaccess", "...x",
            "css/.hidden", "a/b/.c/d", ".dir/file.txt", "css/.git/config", ".well-known/security.txt",
            "%2egitkeep", "%2Egitkeep", "css/%2ehidden", "%2ehidden-dir/inside.txt", "css%2F.hidden", "css%2f%2ehidden",
            "css\\.hidden", "\\.hidden",
            ".", "..", "css/..", "./x", "css/./x",
            "%zz", "%2", "css/%", "%e0%a4%a"})
    void refusesAnyPathWithAHiddenSegment(String path) {
        assertThat(NoDotfilesResourceResolver.isHidden(path)).as(path).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "tokens.css", "css/tokens.css", "js/theme.js", "img/logo.svg", "img/icons/sprite.svg",
            "css/tokens.min.css", "a.b/c.d", "dots.in.the.middle", "a..b", "x/y.z/w", "name-with-dash_and_underscore",
            "css/tokens%2Ecss", "%41", "caf%C3%A9/menu.txt"})
    void letsOrdinaryPathsThrough(String path) {
        assertThat(NoDotfilesResourceResolver.isHidden(path)).as(path).isFalse();
    }

    @Test
    void aRefusedPathNeverReachesTheRestOfTheChain() {
        RecordingChain chain = new RecordingChain();

        assertThat(resolver.resolveResource(null, ".gitkeep", List.of(), chain)).isNull();
        assertThat(resolver.resolveResource(null, "css/%2ehidden", List.of(), chain)).isNull();
        assertThat(resolver.resolveUrlPath(".env", List.of(), chain)).isNull();

        assertThat(chain.asked).isEmpty();
    }

    @Test
    void anOrdinaryPathIsPassedOnAndTheChainsAnswerIsReturned() {
        RecordingChain chain = new RecordingChain();

        assertThat(resolver.resolveResource(null, "css/tokens.css", List.of(), chain)).isSameAs(chain.answer);
        assertThat(resolver.resolveUrlPath("css/tokens.css", List.of(), chain)).isEqualTo("css/tokens.css");

        assertThat(chain.asked).containsExactly("css/tokens.css", "css/tokens.css");
    }
}
