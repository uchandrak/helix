/**
 * Copyright (C) 2012 LinkedIn Inc <opensource@linkedin.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedin.helix.webapp.resources;

import java.io.IOException;
import java.util.Map;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;

import com.linkedin.helix.HelixDataAccessor;
import com.linkedin.helix.HelixException;
import com.linkedin.helix.model.InstanceConfig;
import com.linkedin.helix.model.LiveInstance;
import com.linkedin.helix.tools.ClusterSetup;
import com.linkedin.helix.webapp.RestAdminApplication;

public class InstancesResource extends Resource
{
  public static final String _instanceName = "instanceName";
  public static final String _instanceNames = "instanceNames";

  public InstancesResource(Context context, Request request, Response response)
  {
    super(context, request, response);
    getVariants().add(new Variant(MediaType.TEXT_PLAIN));
    getVariants().add(new Variant(MediaType.APPLICATION_JSON));
  }

  @Override
  public boolean allowGet()
  {
    return true;
  }

  @Override
  public boolean allowPost()
  {
    return true;
  }

  @Override
  public boolean allowPut()
  {
    return false;
  }

  @Override
  public boolean allowDelete()
  {
    return false;
  }

  @Override
  public Representation represent(Variant variant)
  {
    StringRepresentation presentation = null;
    try
    {
      String clusterName = (String) getRequest().getAttributes().get("clusterName");
      presentation = getInstancesRepresentation(clusterName);
    }

    catch (Exception e)
    {
      String error = ClusterRepresentationUtil.getErrorAsJsonStringFromException(e);
      presentation = new StringRepresentation(error, MediaType.APPLICATION_JSON);

      e.printStackTrace();
    }
    return presentation;
  }

  StringRepresentation getInstancesRepresentation(String clusterName)
      throws JsonGenerationException, JsonMappingException, IOException
  {
    HelixDataAccessor accessor = ClusterRepresentationUtil.getClusterDataAccessor(clusterName);
    Map<String, LiveInstance> liveInstancesMap = accessor.getChildValuesMap(accessor.keyBuilder().liveInstances());
    Map<String, InstanceConfig> instanceConfigsMap = accessor.getChildValuesMap(accessor.keyBuilder().instanceConfigs());

    for (String instanceName : instanceConfigsMap.keySet())
    {
      boolean isAlive = liveInstancesMap.containsKey(instanceName);
      instanceConfigsMap.get(instanceName).getRecord().setSimpleField("Alive", isAlive + "");
    }

    StringRepresentation representation = new StringRepresentation(
        ClusterRepresentationUtil.ObjectToJson(instanceConfigsMap.values()), MediaType.APPLICATION_JSON);

    return representation;
  }

  @Override
  public void acceptRepresentation(Representation entity)
  {
    try
    {
      String clusterName = (String) getRequest().getAttributes().get("clusterName");

      Form form = new Form(entity);

      Map<String, String> paraMap = ClusterRepresentationUtil
          .getFormJsonParametersWithCommandVerified(form,
              ClusterSetup.addInstance);

      ClusterSetup setupTool = new ClusterSetup(RestAdminApplication.getZkClient());
      if (paraMap.containsKey(_instanceName))
      {
        setupTool.addInstanceToCluster(clusterName, paraMap.get(_instanceName));
      } else if (paraMap.containsKey(_instanceNames))
      {
        setupTool.addInstancesToCluster(clusterName, paraMap.get(_instanceNames).split(";"));
      } else
      {
        throw new HelixException("Json paramaters does not contain '" + _instanceName + "' or '"
            + _instanceNames + "' ");
      }

      // add cluster
      getResponse().setEntity(getInstancesRepresentation(clusterName));
      getResponse().setStatus(Status.SUCCESS_OK);
    }

    catch (Exception e)
    {
      getResponse().setEntity(ClusterRepresentationUtil.getErrorAsJsonStringFromException(e),
          MediaType.APPLICATION_JSON);
      getResponse().setStatus(Status.SUCCESS_OK);
    }
  }
}
