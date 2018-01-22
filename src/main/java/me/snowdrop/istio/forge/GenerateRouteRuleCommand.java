/**
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 * <p>
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package me.snowdrop.istio.forge;

import java.util.Collections;
import java.util.stream.Collectors;

import javax.inject.Inject;

import io.fabric8.forge.kubernetes.AbstractKubernetesCommand;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.utils.Objects;
import me.snowdrop.istio.api.model.IstioResource;
import me.snowdrop.istio.api.model.IstioResourceBuilder;
import me.snowdrop.istio.api.model.IstioResourceFluent;
import me.snowdrop.istio.client.IstioClient;
import me.snowdrop.istio.client.IstioClientFactory;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.projects.Projects;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;

/**
 * @author <a href="claprun@redhat.com">Christophe Laprun</a>
 */
public class GenerateRouteRuleCommand extends AbstractKubernetesCommand {

    public static String CATEGORY = "Istio";

    private static final String namespace = "istio-system";
    private IstioClient istioClient;

    @Inject
    private ProjectFactory projectFactory;

    @Inject
    @WithAttributes(required = true, name = "name", label = "The base name for the RouteRule")
    UIInput<String> name;

    @Inject
    @WithAttributes(name = "generateName", label = "Use base name as automatic name generation basis?", defaultValue = "false")
    UIInput<Boolean> generate;

    @Inject
    @WithAttributes(required = true, name = "destinationName", label = "The name of the target service")
    UIInput<String> destinationName;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.from(super.getMetadata(context), getClass())
                .category(Categories.create(CATEGORY))
                .name(CATEGORY + ": Generate RouteRule")
                .description("Generates an Istio RouteRule");
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        super.initializeUI(builder);

        // populate autocompletion options
        destinationName.setCompleter((context, input, value) -> {
            final ServiceList services = getKubernetes().services().list();
            if (services != null) {
                return services.getItems().stream()
                        .map(KubernetesHelper::getName)
                        .filter(name -> name.startsWith(input.getValue().toString()))
                        .sorted()
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        });

        builder.add(name);
        builder.add(generate);
    }

    public String getNamespace() {
        return namespace;
    }


    public IstioClient getIstioClient() {
        if (istioClient == null) {
            final String kubernetesAddress = getKubernetes().getMasterUrl().toExternalForm();
            // create an OpenShift client to connect to the cluster
            Config config = new ConfigBuilder()
                    .withMasterUrl(kubernetesAddress)
                    .withUsername("admin")
                    .withPassword("admin")
                    .withNamespace("istio-system")
                    .build();
            istioClient = IstioClientFactory.defaultClient(config);
        }
        Objects.notNull(istioClient, "Istio client");
        return istioClient;
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        final UIContext uiContext = context.getUIContext();
        final Project project = Projects.getSelectedProject(projectFactory, uiContext);


        // build a new RouteRule using fluent builder API
        final IstioResourceFluent.MetadataNested<IstioResourceBuilder> metadataBuilder = new IstioResourceBuilder()
                .withNewMetadata();
        if (generate.getValue()) {
            metadataBuilder.withGenerateName(name.getValue());
        } else {
            metadataBuilder.withName(name.getValue());
        }


        final IstioResource resource = metadataBuilder
                .endMetadata()
                .withNewRouteRuleSpec()
                .withNewDestination()
                .withName(destinationName.getValue())
                .endDestination()
                .addNewRoute()
                .withWeight(100)
                .endRoute()
                .endRouteRuleSpec()
                .build();

        return Results.success("RouteRule " + name + "created", getIstioClient().registerCustomResource(resource));
    }

    @Override
    protected boolean isProjectRequired() {
        return false;
    }

    @Override
    protected ProjectFactory getProjectFactory() {
        return projectFactory;
    }
}
