package com.acove.blog;

import java.nio.file.Path;
import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.acove.blog.common.utils.ImageThumbnailService;

@SpringBootApplication
public class AcoveApplication {

	private static final String BACKFILL_ARGUMENT = "--backfill-image-thumbnails";

	public static void main(String[] args) {
		if (Arrays.asList(args).contains(BACKFILL_ARGUMENT)) {
			String uploadDirectory = System.getenv().getOrDefault("BLOG_UPLOAD_DIR", "./uploads");
			ImageThumbnailService.BackfillSummary summary = ImageThumbnailService.backfill(Path.of(uploadDirectory));
			System.out.printf("Image thumbnail backfill: %d originals scanned, %d variants created, %d variants repaired, %d temporary files cleaned, %d failures.%n",
					summary.originalsVisited(), summary.variantsGenerated(), summary.variantsRepaired(),
					summary.temporaryFilesCleaned(), summary.failures());
			if (summary.failures() > 0) {
				System.exit(1);
			}
			return;
		}
		SpringApplication.run(AcoveApplication.class, args);
	}

}
