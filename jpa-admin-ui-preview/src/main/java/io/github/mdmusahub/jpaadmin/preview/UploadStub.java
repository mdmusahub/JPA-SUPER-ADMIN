package io.github.mdmusahub.jpaadmin.preview;

import static io.github.mdmusahub.jpaadmin.preview.ChromeFixtures.BASE;

import io.github.mdmusahub.jpaadmin.storage.UploadMessages;
import io.github.mdmusahub.jpaadmin.storage.UploadNames;
import io.github.mdmusahub.jpaadmin.web.view.UploadResult;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

/**
 * The stub upload endpoint of the preview (task U6, {@code docs/FIXTURES.md} section 8): it lets {@code uploader.js} show the
 * states that only exist in the browser (idle, dragging, uploading with progress, done, error) without a storage, a ticket or a
 * login. <b>It stores nothing</b>: the file is not read, not written and not kept; the answer is made up.
 *
 * <p>The state comes from the ticket the fixture page printed for the field ({@code success-image}, {@code success-file},
 * {@code error-too-large}, {@code error-type}, {@code error-ticket-expired}, {@code slow}); any other ticket is
 * {@code success-image}. The messages are the real {@link UploadMessages}, so the preview shows the sentences the console says.
 * It also serves one small picture and one small document for the fixtures' addresses ({@code {base}/files/*.png} and
 * {@code *.pdf}), so a thumbnail has something to show, and answers 404 for {@code missing.png}, so the "file not found" look can
 * be seen.
 *
 * <p>Like every preview class it lives only in this unpublished module and is never shipped; the preview refuses to start under
 * the {@code prod} profile (ARCH-11).
 */
@Controller
class UploadStub {

    static final String SUCCESS_IMAGE = "success-image";
    static final String SUCCESS_FILE = "success-file";
    static final String ERROR_TOO_LARGE = "error-too-large";
    static final String ERROR_TYPE = "error-type";
    static final String ERROR_TICKET_EXPIRED = "error-ticket-expired";
    static final String SLOW = "slow";

    /** The six states of FIXTURES.md section 8, in the order of the document. */
    static final List<String> STATES = List.of(SUCCESS_IMAGE, SUCCESS_FILE, ERROR_TOO_LARGE, ERROR_TYPE, ERROR_TICKET_EXPIRED, SLOW);

    private static final byte[] PICTURE = picture();
    private static final byte[] DOCUMENT = ("%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n")
            .getBytes(StandardCharsets.ISO_8859_1);

    @PostMapping(BASE + "/uploads")
    @ResponseBody
    ResponseEntity<UploadResult> upload(@RequestParam(name = "ticket", required = false) String ticket,
                                        @RequestParam(name = "file", required = false) MultipartFile file) throws InterruptedException {
        String state = ticket != null && STATES.contains(ticket) ? ticket : SUCCESS_IMAGE;
        String name = UploadNames.display(file == null ? null : file.getOriginalFilename());
        long size = file == null ? 0 : file.getSize();
        return switch (state) {
            case SUCCESS_FILE -> ResponseEntity.ok(UploadResult.success(BASE + "/files/brochure.pdf", name, size));
            case ERROR_TOO_LARGE -> failure(HttpStatus.PAYLOAD_TOO_LARGE, UploadMessages.tooLarge(2L * 1024 * 1024));
            case ERROR_TYPE -> failure(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    UploadMessages.unknownType(List.of("image/png", "image/jpeg", "image/gif", "image/webp")));
            case ERROR_TICKET_EXPIRED -> failure(HttpStatus.FORBIDDEN, UploadMessages.ticketExpired());
            case SLOW -> {
                Thread.sleep(5000);
                yield ResponseEntity.ok(UploadResult.success(BASE + "/files/cover.png", name, size));
            }
            default -> ResponseEntity.ok(UploadResult.success(BASE + "/files/cover.png", name, size));
        };
    }

    private static ResponseEntity<UploadResult> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(UploadResult.failure(message));
    }

    /** One picture for every {@code .png}, one document for every {@code .pdf}, nothing for {@code missing.png}. */
    @GetMapping(BASE + "/files/{name:.+}")
    ResponseEntity<byte[]> file(@PathVariable String name) {
        if (name.equals("missing.png")) {
            return ResponseEntity.notFound().build();
        }
        boolean picture = name.endsWith(".png");
        if (!picture && !name.endsWith(".pdf")) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(picture ? MediaType.IMAGE_PNG : MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, picture ? "inline" : "attachment")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(picture ? PICTURE : DOCUMENT);
    }

    /** A 64 by 64 picture made with the JDK, so no image file has to ship with the preview. */
    private static byte[] picture() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                image.setRGB(x, y, ((x / 8 + y / 8) % 2 == 0) ? 0x0d9f6e : 0xe6f6ef);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return out.toByteArray();
    }
}
