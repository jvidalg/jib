/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.common.base.Suppliers;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/** Tests for {@link BuildSteps}. */
public class BuildStepsTest {
  private Supplier<ExecutorService> oldExecutorService;
  private BuildConfiguration.Builder buildConfigurationBuilder;
  private ExecutorService mockExecutorService;

  @Before
  public void setUp() throws InvalidImageReferenceException {
    oldExecutorService = BuildSteps.executorServiceFactory;
    mockExecutorService = Mockito.mock(ExecutorService.class);

    buildConfigurationBuilder =
        BuildConfiguration.builder()
            .setBaseImageConfiguration(
                ImageConfiguration.builder(ImageReference.parse("busybox")).build())
            .setTargetImageConfiguration(
                ImageConfiguration.builder(ImageReference.parse("busybox")).build())
            .setBaseImageLayersCacheDirectory(Paths.get("/"))
            .setApplicationLayersCacheDirectory(Paths.get("/"));
  }

  @After
  public void tearDown() {
    BuildSteps.executorServiceFactory = oldExecutorService;
  }

  /** Verify that an internally-created ExecutorService is shutdown. */
  @Test
  public void testRun_createdExecutor()
      throws InterruptedException, ExecutionException, IOException {
    BuildSteps.executorServiceFactory = Suppliers.ofInstance(mockExecutorService);
    BuildConfiguration buildConfiguration = buildConfigurationBuilder.build();
    BuildSteps buildSteps = new BuildSteps("description", buildConfiguration, executor -> null);

    buildSteps.run();

    Mockito.verify(mockExecutorService).shutdown();
  }

  /** Verify that a provided ExecutorService is not shutdown. */
  @Test
  public void testRun_configuredExecutor()
      throws InterruptedException, ExecutionException, IOException {
    BuildSteps.executorServiceFactory =
        () -> {
          throw new AssertionError();
        };
    BuildConfiguration buildConfiguration =
        buildConfigurationBuilder.setExecutorService(mockExecutorService).build();
    BuildSteps buildSteps = new BuildSteps("description", buildConfiguration, executor -> null);

    buildSteps.run();

    Mockito.verify(mockExecutorService, Mockito.never()).shutdown();
  }
}
