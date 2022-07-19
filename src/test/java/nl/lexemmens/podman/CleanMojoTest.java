package nl.lexemmens.podman;

import nl.lexemmens.podman.config.image.single.TestSingleImageConfigurationBuilder;
import nl.lexemmens.podman.config.image.single.SingleImageConfiguration;
import nl.lexemmens.podman.config.podman.PodmanConfiguration;
import nl.lexemmens.podman.config.podman.TestPodmanConfigurationBuilder;
import nl.lexemmens.podman.config.skopeo.SkopeoConfiguration;
import nl.lexemmens.podman.enumeration.TlsVerify;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@RunWith(MockitoJUnitRunner.class)
public class CleanMojoTest extends AbstractMojoTest {

    @InjectMocks
    private CleanMojo cleanMojo;

    @Test
    public void testSkipAllActions() throws MojoExecutionException {
        configureMojo(true, false, null);

        cleanMojo.execute();

        verify(log, times(1)).info(Mockito.eq("Podman actions are skipped."));
    }

    @Test
    public void testSkipClean() throws MojoExecutionException {
        configureMojo(false, true, null);

        cleanMojo.execute();

        verify(log, times(1)).info(Mockito.eq("Cleaning local storage is skipped."));
    }

    @Test
    public void testCleanWithoutCustomRoot() throws MojoExecutionException {
        configureMojo(false, false, null);

        cleanMojo.execute();

        verify(log, times(1)).info(Mockito.eq("Not cleaning up local storage as default storage location is being used."));
    }

    @Test
    public void testCleanWithCustomRoot() throws MojoExecutionException {
        File customRoot = new File("");

        configureMojo(false, false, customRoot);

        when(serviceHubFactory.createServiceHub(isA(Log.class), isA(MavenProject.class), isA(MavenFileFilter.class), isA(PodmanConfiguration.class), isA(SkopeoConfiguration.class), isA(Settings.class), isA(SettingsDecrypter.class), isA(MavenProjectHelper.class))).thenReturn(serviceHub);
        when(serviceHub.getBuildahExecutorService()).thenReturn(buildahExecutorService);

        cleanMojo.execute();

        verify(log, times(1)).info(Mockito.eq("Cleaning up " + customRoot.getAbsolutePath() + "..."));
        verify(serviceHub, times(1)).getBuildahExecutorService();
        verify(buildahExecutorService, times(1)).cleanupLocalContainerStorage();
    }

    private void configureMojo(boolean skipAll, boolean skipClean, File customRoot) {
        SingleImageConfiguration image = new TestSingleImageConfigurationBuilder("sample")
                .setUseMavenProjectVersion(true)
                .setContainerfileDir(DEFAULT_CONTAINERFILE_DIR)
                .build();

        List<SingleImageConfiguration> images = Collections.singletonList(image);

        cleanMojo.podman = new TestPodmanConfigurationBuilder().setTlsVerify(TlsVerify.NOT_SPECIFIED).setRoot(customRoot).build();
        cleanMojo.skip = skipAll;
        cleanMojo.skipAuth = true;
        cleanMojo.skipClean = skipClean;
        cleanMojo.images = images;
    }
}
