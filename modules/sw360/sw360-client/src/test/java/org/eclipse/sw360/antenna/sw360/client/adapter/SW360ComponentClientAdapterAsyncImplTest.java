/*
 * Copyright (c) Bosch.IO GmbH 2020.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.antenna.sw360.client.adapter;

import org.eclipse.sw360.antenna.http.utils.FailedRequestException;
import org.eclipse.sw360.antenna.http.utils.HttpConstants;
import org.eclipse.sw360.antenna.sw360.client.rest.MultiStatusResponse;
import org.eclipse.sw360.antenna.sw360.client.rest.SW360ComponentClient;
import org.eclipse.sw360.antenna.sw360.client.utils.SW360ClientException;
import org.eclipse.sw360.antenna.sw360.client.rest.resource.LinkObjects;
import org.eclipse.sw360.antenna.sw360.client.rest.resource.Self;
import org.eclipse.sw360.antenna.sw360.client.rest.resource.components.SW360Component;
import org.eclipse.sw360.antenna.sw360.client.rest.resource.components.SW360SparseComponent;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.sw360.antenna.sw360.client.utils.FutureUtils.block;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SW360ComponentClientAdapterAsyncImplTest {
    private final static String COMPONENT_ID = "12345";
    private final static String COMPONENT_NAME = "componentName";

    private SW360ComponentClientAdapterAsync componentClientAdapter;

    private SW360ComponentClient componentClient;

    private SW360SparseComponent sparseComponent;
    private SW360Component component;

    @Before
    public void setUp() {
        componentClient = mock(SW360ComponentClient.class);
        componentClientAdapter = new SW360ComponentClientAdapterAsyncImpl(componentClient);
        sparseComponent = new SW360SparseComponent();
        component = new SW360Component();
    }

    @Test
    public void testGetOrCreateComponentByID() {
        SW360Component componentFromRelease = mock(SW360Component.class);
        when(componentFromRelease.getId()).thenReturn(COMPONENT_ID);
        when(componentClient.getComponent(COMPONENT_ID))
                .thenReturn(CompletableFuture.completedFuture(component));

        Optional<SW360Component> optResult = block(componentClientAdapter.getOrCreateComponent(componentFromRelease));
        assertThat(optResult).contains(component);
    }

    @Test
    public void testGetOrCreateComponentByName() {
        SW360Component componentFromRelease = mock(SW360Component.class);
        when(componentFromRelease.getId()).thenReturn(null);
        when(componentFromRelease.getName()).thenReturn(COMPONENT_NAME);
        LinkObjects linkObjects = makeLinkObjects();
        sparseComponent.setName(COMPONENT_NAME)
                .setLinks(linkObjects);
        component.setName(COMPONENT_NAME);

        when(componentClient.getComponent(COMPONENT_ID))
                .thenReturn(CompletableFuture.completedFuture(component));
        when(componentClient.searchByName(COMPONENT_NAME))
                .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(sparseComponent)));

        Optional<SW360Component> optResult = block(componentClientAdapter.getOrCreateComponent(componentFromRelease));
        assertThat(optResult).contains(component);
    }

    @Test
    public void testGetOrCreateComponentCreateNew() {
        SW360Component componentFromRelease = mock(SW360Component.class);
        when(componentFromRelease.getId()).thenReturn(null);
        when(componentFromRelease.getName()).thenReturn(COMPONENT_NAME);
        when(componentFromRelease.getCategories()).thenReturn(Collections.singleton("Antenna"));
        when(componentClient.searchByName(COMPONENT_NAME))
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
        when(componentClient.createComponent(componentFromRelease))
                .thenReturn(CompletableFuture.completedFuture(component));

        Optional<SW360Component> optResult = block(componentClientAdapter.getOrCreateComponent(componentFromRelease));
        assertThat(optResult).contains(component);
    }

    @Test
    public void testCreateComponent() {
        component.setName(COMPONENT_NAME);
        component.setCategories(Collections.singleton("Antenna"));
        when(componentClient.createComponent(component))
                .thenReturn(CompletableFuture.completedFuture(component));

        SW360Component createdComponent = block(componentClientAdapter.createComponent(this.component));

        assertThat(createdComponent).isEqualTo(component);
        verify(componentClient).createComponent(component);
    }

    @Test(expected = SW360ClientException.class)
    public void testCreateComponentNull() {
        block(componentClientAdapter.createComponent(this.component));
    }

    @Test
    public void testCreateComponentInvalid() {
        component.setName("");

        CompletableFuture<SW360Component> futComponent = componentClientAdapter.createComponent(this.component);
        try {
            block(futComponent);
            fail("Invalid component not detected.");
        } catch (SW360ClientException e) {
            assertThat(e.getMessage()).contains("invalid component");
        }
    }

    @Test
    public void testGetComponentById() {
        when(componentClient.getComponent(COMPONENT_ID))
                .thenReturn(CompletableFuture.completedFuture(component));

        Optional<SW360Component> componentById = block(componentClientAdapter.getComponentById(COMPONENT_ID));

        assertThat(componentById).isPresent();
        assertThat(componentById).hasValue(component);
        verify(componentClient).getComponent(COMPONENT_ID);
    }

    @Test
    public void testGetComponentByIdNotFound() {
        CompletableFuture<SW360Component> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new FailedRequestException("tag", HttpConstants.STATUS_ERR_NOT_FOUND));
        when(componentClient.getComponent(COMPONENT_ID)).thenReturn(failedFuture);

        Optional<SW360Component> componentById = block(componentClientAdapter.getComponentById(COMPONENT_ID));
        assertThat(componentById).isNotPresent();
    }

    @Test
    public void testGetComponentByName() {
        LinkObjects linkObjects = makeLinkObjects();
        sparseComponent.setName(COMPONENT_NAME)
                .setLinks(linkObjects);

        component.setName(COMPONENT_NAME);

        when(componentClient.getComponent(COMPONENT_ID))
                .thenReturn(CompletableFuture.completedFuture(component));
        when(componentClient.searchByName(COMPONENT_NAME))
                .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(sparseComponent)));

        Optional<SW360Component> componentByName = block(componentClientAdapter.getComponentByName(COMPONENT_NAME));

        assertThat(componentByName).isPresent();
        assertThat(componentByName).hasValue(component);
        verify(componentClient).getComponent(COMPONENT_ID);
        verify(componentClient).searchByName(COMPONENT_NAME);
    }

    @Test
    public void testGetComponents() {
        when(componentClient.getComponents())
                .thenReturn(CompletableFuture.completedFuture(Collections.singletonList(sparseComponent)));

        List<SW360SparseComponent> components = block(componentClientAdapter.getComponents());

        assertThat(components).hasSize(1);
        assertThat(components).containsExactly(sparseComponent);
        verify(componentClient).getComponents();
    }

    @Test
    public void testDeleteComponentsEmptyCollection() {
        MultiStatusResponse response = block(componentClientAdapter.deleteComponents(Collections.emptySet()));

        assertThat(response.responseCount()).isEqualTo(0);
        verifyZeroInteractions(componentClient);
    }

    @Test
    public void testDeleteMultipleComponents() {
        Collection<String> componentsToDelete = Arrays.asList(COMPONENT_ID, "DEL-1", "DEL-2");
        MultiStatusResponse expResponse = new MultiStatusResponse(Collections.singletonMap(COMPONENT_ID, 200));
        when(componentClient.deleteComponents(componentsToDelete))
                .thenReturn(CompletableFuture.completedFuture(expResponse));

        MultiStatusResponse response = block(componentClientAdapter.deleteComponents(componentsToDelete));
        assertThat(response).isEqualTo(expResponse);
    }

    @Test
    public void testDeleteSingleComponentSuccess() {
        MultiStatusResponse response = new MultiStatusResponse(Collections.singletonMap(COMPONENT_ID, 202));
        Set<String> idsToDelete = Collections.singleton(COMPONENT_ID);
        when(componentClient.deleteComponents(idsToDelete))
                .thenReturn(CompletableFuture.completedFuture(response));

        block(componentClientAdapter.deleteComponent(COMPONENT_ID));
        verify(componentClient).deleteComponents(idsToDelete);
    }

    @Test
    public void testDeleteSingleComponentFailureStatus() {
        MultiStatusResponse response =
                new MultiStatusResponse(Collections.singletonMap(COMPONENT_ID, HttpConstants.STATUS_ERR_BAD_REQUEST));
        Set<String> idsToDelete = Collections.singleton(COMPONENT_ID);
        when(componentClient.deleteComponents(idsToDelete))
                .thenReturn(CompletableFuture.completedFuture(response));

        try {
            block(componentClientAdapter.deleteComponent(COMPONENT_ID));
            fail("Failed delete operation not detected");
        } catch (SW360ClientException e) {
            assertThat(e.getCause()).isInstanceOf(FailedRequestException.class);
            FailedRequestException reqEx = (FailedRequestException) e.getCause();
            assertThat(reqEx.getStatusCode()).isEqualTo(HttpConstants.STATUS_ERR_BAD_REQUEST);
            assertThat(reqEx.getTag()).isEqualTo("delete component " + COMPONENT_ID);
        }
    }

    @Test
    public void testDeleteSingleComponentUnexpectedResponse() {
        Map<String, Integer> statusMap = new HashMap<>();
        statusMap.put(COMPONENT_ID, HttpConstants.STATUS_OK);
        statusMap.put("unexpectedId", HttpConstants.STATUS_OK);
        MultiStatusResponse response = new MultiStatusResponse(statusMap);
        when(componentClient.deleteComponents(Collections.singleton(COMPONENT_ID)))
                .thenReturn(CompletableFuture.completedFuture(response));

        try {
            block(componentClientAdapter.deleteComponent(COMPONENT_ID));
            fail("Invalid response not detected");
        } catch (SW360ClientException e) {
            assertThat(e.getMessage()).contains("Unexpected multi-status response", response.toString());
        }
    }

    @Test
    public void testDeleteSingleComponentResponseNoResourceId() {
        MultiStatusResponse response =
                new MultiStatusResponse(Collections.singletonMap("unexpectedId", HttpConstants.STATUS_ERR_BAD_REQUEST));
        when(componentClient.deleteComponents(Collections.singleton(COMPONENT_ID)))
                .thenReturn(CompletableFuture.completedFuture(response));

        try {
            block(componentClientAdapter.deleteComponent(COMPONENT_ID));
            fail("Invalid response not detected");
        } catch (SW360ClientException e) {
            assertThat(e.getMessage()).contains("Unexpected multi-status response", response.toString());
        }
    }

    private static LinkObjects makeLinkObjects() {
        String componentHref = "url/" + COMPONENT_ID;
        Self componentSelf = new Self().setHref(componentHref);
        return new LinkObjects()
                .setSelf(componentSelf);
    }
}
