package nl.lexemmens.podman;

import nl.lexemmens.podman.config.ImageConfiguration;
import nl.lexemmens.podman.service.ServiceHub;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * SaveMojo for exporting container images to the file system
 */
@Mojo(name = "save", defaultPhase = LifecyclePhase.NONE)
public class SaveMojo extends AbstractPodmanMojo {

    private static final String SAVE_CMD = "save";
    private static final String FORMAT_CMD = "--format";
    private static final String OCI_ARCHIVE_CMD = "oci-archive";
    private static final String OUTPUT_CMD = "-o";

    @Parameter(property = "podman.skip.save", defaultValue = "false")
    private boolean skipSave;

    @Override
    public void executeInternal(ServiceHub hub) throws MojoExecutionException {
        if(skipSave){
            getLog().info("Saving container images is skipped.");
            return;
        }

        ImageConfiguration imageConfiguration = getImageConfiguration();
        if(imageConfiguration.getFullImageNames().isEmpty()) {
            getLog().info("There are no container images to export.");
            return;
        }

        exportContainerImage(imageConfiguration, hub);
    }

    private void exportContainerImage(ImageConfiguration imageConfiguration, ServiceHub hub) throws MojoExecutionException {
        Path targetPodmanDir = Paths.get(outputDirectory.toURI()).resolve(PODMAN);
        createTargetFolder(targetPodmanDir);

        for(String image : imageConfiguration.getFullImageNames()) {
            String archiveName = String.format("%s.tar.gz", normaliseImageName(image));

            getLog().info("Exporting image " + image + " to " + targetPodmanDir + "/" + archiveName);
            hub.getCommandExecutorService().runCommand(targetPodmanDir.toFile(), PODMAN, SAVE_CMD, FORMAT_CMD, OCI_ARCHIVE_CMD, OUTPUT_CMD, archiveName, image);
        }

        getLog().info("Container images exported successfully.");
    }

    private void createTargetFolder(Path targetPodmanDir) throws MojoExecutionException {
        try {
            // Does not fail if folder already exists
            Files.createDirectories(targetPodmanDir);
        } catch(IOException e) {
            String msg = "Failed to create directory '" + targetPodmanDir + "'. An IOException occurred: " + e.getMessage();
            getLog().error(msg, e);
            throw new MojoExecutionException(msg, e);
        }
    }

    private String normaliseImageName(String fullImageName) {
        String[] imageNameParts = fullImageName.split("\\/");
        String tagAndVersion = imageNameParts[imageNameParts.length - 1];
        return tagAndVersion.replaceAll("[\\.\\/\\-\\*:]", "_");
    }
}
