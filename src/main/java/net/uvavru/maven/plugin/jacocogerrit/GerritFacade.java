package net.uvavru.maven.plugin.jacocogerrit;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.ReaderInterceptor;
import javax.ws.rs.ext.ReaderInterceptorContext;

import net.uvavru.maven.plugin.jacocogerrit.model.PatchCoverageInput;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoFailureException;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GerritFacade.
 */
public class GerritFacade {
    private static final Logger LOG = LoggerFactory.getLogger(GerritFacade.class);
    private static final String COMMIT_MSG = "/COMMIT_MSG";

    private final List<String> gerritFileList = new ArrayList<>();
    private final JacocoGerritMojo mojo;

    private final WebTarget target;

    public GerritFacade(JacocoGerritMojo jacocoGerritMojo) {
        mojo = jacocoGerritMojo;
        target = ClientBuilder.newClient(new ClientConfig()
                                                 .register(new XSSFilter())
                                                 .register(JacksonFeature.class)
                                                 .register(HttpAuthenticationFeature.universal(mojo.getUsername(),
                                                                                               mojo.getPassword())
                                                 )
        ).target(UriBuilder.fromPath(mojo.getBasePath())
                         .host(mojo.getHost())
                         .port(mojo.getPort())
                         .scheme(mojo.getScheme()))
                .path(mojo.isAnonymous() ? "" : "a")
                .path("changes")
                .path(mojo.getProjectName() + "~" + mojo.getBranchName() + "~" + mojo.getChangeId())
                .path("revisions")
                .path(mojo.getRevisionId());

        if (LOG.isDebugEnabled()) {
            target.register(new LoggingFilter(java.util.logging.Logger.getLogger(getClass().getName()), true));
        }
    }

    public List<String> listFiles() throws MojoFailureException {
        if (!gerritFileList.isEmpty()) {
            LOG.debug("File list already filled. Not calling Gerrit.");
        } else {

            Response response = target.path("files").request().get();
            if (Response.Status.Family.SUCCESSFUL != response.getStatusInfo().getFamily()) {
                Utils.logErrorAndThrow(LOG, MojoFailureException::new,
                                       "Received an error while listing files: " + response);
            }

            try {
                JsonNode object = response.readEntity(JsonNode.class);

                Iterable<Map.Entry<String, JsonNode>> iterable = object::fields;
                StreamSupport.stream(iterable.spliterator(), false)
                        .filter(stringJsonNodeEntry -> !COMMIT_MSG.equals(stringJsonNodeEntry.getKey()))
                        .map(Map.Entry::getKey)
                        .peek(s -> LOG.info("Modified file received from Gerrit: {}", s))
                        .forEachOrdered(gerritFileList::add);
            } catch (ProcessingException e) {
                Utils.logErrorAndThrow(LOG, MojoFailureException::new, "Cannot read a response from: " + response, e);
            }
        }
        return Collections.unmodifiableList(gerritFileList);
    }

    public void setCoverage(PatchCoverageInput patchCoverageInput) throws MojoFailureException {

        Response response = target.path("coverage").request().post(Entity.json(patchCoverageInput));
        response.close();
        if (Response.Status.Family.SUCCESSFUL != response.getStatusInfo().getFamily()) {
            Utils.logErrorAndThrow(LOG, MojoFailureException::new,
                                   "Received an error while setting the coverage: " + response);
        }
    }

    public static class XSSFilter implements ReaderInterceptor {
        private static final String XSS_PREFIX = ")]}'";

        @Override
        public Object aroundReadFrom(ReaderInterceptorContext readerInterceptorContext)
                throws IOException, WebApplicationException {
            InputStream inputStream = new BufferedInputStream(readerInterceptorContext.getInputStream());
            byte[] buffer = new byte[XSS_PREFIX.getBytes().length + 1];
            inputStream.mark(buffer.length);
            for (int total = 0, read = 0; read != -1 && total < buffer.length; total += read) {
                read = inputStream.read(buffer);
            }
            if (!new String(buffer).startsWith(XSS_PREFIX)) {
                inputStream.reset();
            }
            readerInterceptorContext.setInputStream(inputStream);
            return readerInterceptorContext.proceed();
        }

    }

}
