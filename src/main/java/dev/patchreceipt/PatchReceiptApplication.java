package dev.patchreceipt;

import dev.patchreceipt.cli.PatchReceiptCli;
import java.util.Set;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class PatchReceiptApplication {

    private static final Set<String> CLI_COMMANDS = Set.of("verify", "init");

    public static void main(String[] args) {
        if (args.length > 0 && CLI_COMMANDS.contains(args[0])) {
            int exitCode;
            try (var context = new SpringApplicationBuilder(PatchReceiptApplication.class)
                    .web(WebApplicationType.NONE)
                    .bannerMode(Banner.Mode.OFF)
                    .run()) {
                exitCode = context.getBean(PatchReceiptCli.class).execute(args);
            }
            System.exit(exitCode);
            return;
        }
        SpringApplication.run(PatchReceiptApplication.class, args);
    }

}
