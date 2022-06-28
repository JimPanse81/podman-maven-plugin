package nl.lexemmens.podman.service;

import nl.lexemmens.podman.enumeration.TlsVerify;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@ExtendWith(MockitoExtension.class)
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationServiceTest {

    @Rule
    public EnvironmentVariables env = new EnvironmentVariables();

    @Mock
    private Log log;

    @Mock
    private PodmanExecutorService podmanExecutorService;

    @Mock
    private Settings settings;

    @Mock
    private SettingsDecrypter settingsDecrypter;

    private final TlsVerify tlsVerify = TlsVerify.TRUE;

    // In case the UID detection does not succeed, the following uid will be used as default uid
    private static final int DEFAULT_UID = 1000;

    // The uid of the user running the tests, this is needed to determine e.g. /run/user/$UID/
    private int uid = DEFAULT_UID;

    @Before
    public void setup() {
        initMocks(this);
    }

    @Before
    public void before() throws IOException {
        // Backup docker config
        Path dockerConfigFile = Paths.get(System.getProperty("user.home")).resolve(".docker/config.json");
        Path dockerConfigBackupFile = Paths.get(System.getProperty("user.home")).resolve(".docker/config.json.bak");
        if (Files.exists(dockerConfigFile)) {
            Files.move(dockerConfigFile, dockerConfigBackupFile);
        }

        // Determine the uid of the user running the tests
        String idCommand = "id -u " + System.getProperty("user.name");
        Process idProcess = Runtime.getRuntime().exec(idCommand);
        try {
            // wait until the process is finished
            idProcess.waitFor();

            if (idProcess.exitValue() == 0) {
                // process the output
                BufferedReader stdout = new BufferedReader(new InputStreamReader(idProcess.getInputStream()));
                String s = stdout.readLine();

                if (s != null) {
                    uid = Integer.parseInt(s);
                }

                stdout.close();
            }
        } catch (InterruptedException e) {
            // nothing to do
        }
    }

    @After
    public void after() throws IOException {
        // Restore Backup docker config
        Path dockerConfigFile = Paths.get(System.getProperty("user.home")).resolve(".docker/config.json");
        Path dockerConfigBackupFile = Paths.get(System.getProperty("user.home")).resolve(".docker/config.json.bak");
        if (Files.exists(dockerConfigBackupFile)) {
            Files.move(dockerConfigBackupFile, dockerConfigFile);
        }
    }

    @Test
    public void authenticateNullRegistries() {
        AuthenticationService authenticationService = new AuthenticationService(log, podmanExecutorService, settings, settingsDecrypter);
        Assertions.assertThrows(MojoExecutionException.class, () -> authenticationService.authenticate(null));

        verify(log, Mockito.times(1)).info(Mockito.eq("Checking authentication status..."));
        verify(log, Mockito.times(1)).error(Mockito.eq("No registries have been configured but authentication is not skipped. If you want to skip authentication, run again with 'podman.skip.auth' set to true"));
    }

    @Test
    public void authenticateNoRegistries() {
        AuthenticationService authenticationService = new AuthenticationService(log, podmanExecutorService, settings, settingsDecrypter);
        Assertions.assertThrows(MojoExecutionException.class, () -> authenticationService.authenticate(new String[]{}));

        verify(log, Mockito.times(1)).info(Mockito.eq("Checking authentication status..."));
        verify(log, Mockito.times(1)).error(Mockito.eq("No registries have been configured but authentication is not skipped. If you want to skip authentication, run again with 'podman.skip.auth' set to true"));
    }

    @Test
    public void authenticateNoRegistryAuthFileNoEnvVarNoCredentials() throws IOException {
        // Ensure that /run/user/{uid}/containers/auth.json is not present
        Path authFilePath = Paths.get("/run/user/" + uid + "/containers/auth.json");
        Files.deleteIfExists(authFilePath);

        String[] registries = new String[]{"registry.example.com"};

        AuthenticationService authenticationService = new AuthenticationService(log, podmanExecutorService, settings, settingsDecrypter);
        Assertions.assertThrows(MojoExecutionException.class, () -> authenticationService.authenticate(registries));

        verify(log, Mockito.times(1)).info(Mockito.eq("Checking authentication status..."));
        verify(log, Mockito.times(1)).info(Mockito.eq("Authentication file not (yet) present. Authenticating..."));
        verify(log, Mockito.times(0)).error(Mockito.eq("No registries have been configured but authentication is not skipped. If you want to skip authentication, run again with 'podman.skip.auth' set to true"));
    }

    @Test
    public void authenticateNoRegistryAuthFileNoEnvVar() throws IOException, MojoExecutionException {
        String registryName = "registry.example.com";

        Server server = new Server();
        server.setId(registryName);
        server.setUsername("username");
        server.setPassword("password");

        List<Server> serverList = Collections.singletonList(server);

        when(settings.getServer(eq(registryName))).thenReturn(server);
        when(settings.getServers()).thenReturn(serverList);
        when(settings.getProxies()).thenReturn(new ArrayList<>());
        when(settingsDecrypter.decrypt(isA(SettingsDecryptionRequest.class))).thenReturn(createSettingsDecryptionResult(serverList, new ArrayList<>()));

        // Ensure that /run/user/{uid}/containers/auth.json is not present
        Path authFilePath = Paths.get("/run/user/" + uid + "/containers/auth.json");
        Files.deleteIfExists(authFilePath);

        String[] registries = new String[]{"registry.example.com"};

        AuthenticationService authenticationService = new AuthenticationService(log, podmanExecutorService, settings, settingsDecrypter);
        authenticationService.authenticate(registries);

        verify(log, Mockito.times(1)).info(Mockito.eq("Checking authentication status..."));
        verify(log, Mockito.times(1)).info(Mockito.eq("Authentication file not (yet) present. Authenticating..."));
        verify(log, Mockito.times(0)).error(Mockito.eq("No registries have been configured but authentication is not skipped. If you want to skip authentication, run again with 'podman.skip.auth' set to true"));
        verify(podmanExecutorService, times(1)).login(registryName, "username", "password");
    }

    @Test
    public void testCustomRegistryAuthFile() throws MojoExecutionException {
        Path customAuthFile = Paths.get("src", "test", "resources", "validauth.json").toAbsolutePath();
        env.set("REGISTRY_AUTH_FILE", customAuthFile.toString());

        // Set the XDG_RUNTIME_DIR to something else, so that it does not conflict wiht the test
        env.set("XDG_RUNTIME_DIR", "/path/does/not/exist");

        AuthenticationService authenticationService = new AuthenticationService(log, podmanExecutorService, settings, settingsDecrypter);
        authenticationService.authenticate(new String[]{"unknown-registry.example.com"});

        verify(log, Mockito.times(1)).debug(Mockito.eq("Found custom registry authentication file at: " + customAuthFile));
        verify(log, Mockito.times(1)).debug(Mockito.eq("Checking unauthenticated registries..."));
    }

    @Test
    public void testRegistryNotInAuthenticationFile() throws MojoExecutionException {
        Path customAuthFile = Paths.get("src", "test", "resources", "validauth.json").toAbsolutePath();
        env.set("REGISTRY_AUTH_FILE", customAuthFile.toString());

        // Set the XDG_RUNTIME_DIR to something else, so that it does not conflict wiht the test
        env.set("XDG_RUNTIME_DIR", "/path/does/not/exist");

        String registryName = "not-present-registry.example.com";

        Server server = new Server();
        server.setId(registryName);
        server.setUsername("username");
        server.setPassword("password");

        List<Server> serverList = Collections.singletonList(server);

        when(settings.getServer(eq(registryName))).thenReturn(server);
        when(settings.getServers()).thenReturn(serverList);
        when(settings.getProxies()).thenReturn(new ArrayList<>());
        when(settingsDecrypter.decrypt(isA(SettingsDecryptionRequest.class))).thenReturn(createSettingsDecryptionResult(serverList, new ArrayList<>()));

        AuthenticationService authenticationService = new AuthenticationService(log, podmanExecutorService, settings, settingsDecrypter);
        authenticationService.authenticate(new String[]{registryName});

        verify(log, Mockito.times(1)).debug(Mockito.eq("Found custom registry authentication file at: " + customAuthFile));
        verify(log, Mockito.times(1)).debug(Mockito.eq("Checking unauthenticated registries..."));
        verify(log, Mockito.times(1)).debug(Mockito.eq("Authenticating not-present-registry.example.com"));
    }

    @Test
    public void testConfigFileWithoutAuthsSection() {
        Path customAuthFile = Paths.get("src", "test", "resources", "configwithoutauth.json").toAbsolutePath();
        env.set("REGISTRY_AUTH_FILE", customAuthFile.toString());

        // Set the XDG_RUNTIME_DIR to something else, so that it does not conflict wiht the test
        env.set("XDG_RUNTIME_DIR", "/path/does/not/exist");

        AuthenticationService authenticationService = new AuthenticationService(log, podmanExecutorService, settings, settingsDecrypter);
        Assertions.assertThrows(MojoExecutionException.class, () -> authenticationService.authenticate(new String[]{"unknown-registry.example.com"}));

        verify(log, Mockito.times(1)).info(Mockito.eq("Checking authentication status..."));
        verify(log, Mockito.times(1)).debug(Mockito.eq("No authenticated registries were found."));
    }

    @Test
    public void testXdgRuntimeAuthFile() throws MojoExecutionException {
        Path customAuthFileDir = Paths.get("src", "test", "resources").toAbsolutePath();
        Path customAuthFile = customAuthFileDir.resolve("containers/auth.json");
        env.set("XDG_RUNTIME_DIR", customAuthFileDir.toString());

        AuthenticationService authenticationService = new AuthenticationService(log, podmanExecutorService, settings, settingsDecrypter);
        authenticationService.authenticate(new String[]{"unknown-registry.example.com"});

        verify(log, Mockito.times(1)).debug(Mockito.eq("Found default registry authentication file at: " + customAuthFile));
        verify(log, Mockito.times(1)).debug(Mockito.eq("Checking unauthenticated registries..."));
    }

    @Test
    public void testDockerConfig() throws MojoExecutionException, IOException {
        // Set the XDG_RUNTIME_DIR to something else, so that it does not conflict wiht the test
        env.set("XDG_RUNTIME_DIR", "/path/does/not/exist");

        Path fileToUseAsDockerConfigFile = Paths.get("src", "test", "resources", "validauth.json").toAbsolutePath();
        Path dockerConfigFile = Paths.get(System.getProperty("user.home")).resolve(".docker/config.json");
        Files.createDirectories(dockerConfigFile.getParent());
        Files.copy(fileToUseAsDockerConfigFile, dockerConfigFile);

        AuthenticationService authenticationService = new AuthenticationService(log, podmanExecutorService, settings, settingsDecrypter);
        authenticationService.authenticate(new String[]{"unknown-registry.example.com"});

        verify(log, Mockito.times(1)).debug(Mockito.eq("Found Docker registry authentication file at: " + dockerConfigFile));
        verify(log, Mockito.times(1)).debug(Mockito.eq("Checking unauthenticated registries..."));

        // Clean up the temporary docker config file
        Files.deleteIfExists(dockerConfigFile);
    }

    private SettingsDecryptionResult createSettingsDecryptionResult(List<Server> servers, List<Proxy> proxies) {
        return new SettingsDecryptionResult() {
            @Override
            public Server getServer() {
                return null;
            }

            @Override
            public List<Server> getServers() {
                return servers;
            }

            @Override
            public Proxy getProxy() {
                return null;
            }

            @Override
            public List<Proxy> getProxies() {
                return proxies;
            }

            @Override
            public List<SettingsProblem> getProblems() {
                return null;
            }
        };
    }


}
