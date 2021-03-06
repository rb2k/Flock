/*
 * *
 *  Copyright (C) 2014 Open Whisper Systems
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 * /
 */

package org.anhonesteffort.flock.webdav.carddav;

import com.google.common.base.Optional;

import org.anhonesteffort.flock.webdav.WebDavConstants;

import org.anhonesteffort.flock.webdav.AbstractDavComponentStore;
import org.anhonesteffort.flock.webdav.DavClient;
import org.anhonesteffort.flock.webdav.DavComponentStore;
import org.anhonesteffort.flock.webdav.ExtendedMkCol;
import org.anhonesteffort.flock.webdav.PropertyParseException;
import org.apache.commons.httpclient.Header;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.Status;
import org.apache.jackrabbit.webdav.client.methods.DeleteMethod;
import org.apache.jackrabbit.webdav.client.methods.MkColMethod;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Programmer: rhodey
 */
public class CardDavStore extends AbstractDavComponentStore<CardDavCollection>
    implements DavComponentStore<CardDavCollection>
{
  protected Optional<String> addressBookHomeSet = Optional.absent();

  public CardDavStore(String           hostHREF,
                      String           username,
                      String           password,
                      Optional<String> currentUserPrincipal,
                      Optional<String> addressBookHomeSet)
      throws DavException, IOException
  {
    super(hostHREF, username, password, currentUserPrincipal);
    this.addressBookHomeSet = addressBookHomeSet;
  }

  public CardDavStore(DavClient        client,
                      Optional<String> currentUserPrincipal,
                      Optional<String> addressBookHomeSet)
  {
    super(client, currentUserPrincipal);
    this.addressBookHomeSet = addressBookHomeSet;
  }

  @Override
  public Optional<String> getCurrentUserPrincipal() throws DavException, IOException {
    if (currentUserPrincipal.isPresent())
      return currentUserPrincipal;

    DavPropertyNameSet props = new DavPropertyNameSet();
    props.add(WebDavConstants.PROPERTY_NAME_CURRENT_USER_PRINCIPAL);

    String         propFindUri    = getHostHREF().concat("/.well-known/carddav");
    PropFindMethod propFindMethod = new PropFindMethod(propFindUri,
                                                       props,
                                                       PropFindMethod.DEPTH_0);

    try {

      getClient().execute(propFindMethod);
      propFindMethod.getResponseBodyAsMultiStatus();

    } catch (DavException e) {

      if (e.getErrorCode() == DavServletResponse.SC_MOVED_PERMANENTLY) {
        Header locationHeader = propFindMethod.getResponseHeader("location"); // TODO: find constant for this...
        if (locationHeader.getValue() != null) {
          currentUserPrincipal = super.getCurrentUserPrincipal(locationHeader.getValue());
          return currentUserPrincipal;
        }
      }
      else
        throw e;

    } finally {
      propFindMethod.releaseConnection();
    }

    return Optional.absent();
  }

  public Optional<String> getAddressbookHomeSet()
      throws PropertyParseException, DavException, IOException
  {
    if (addressBookHomeSet.isPresent())
      return addressBookHomeSet;

    if (!getCurrentUserPrincipal().isPresent())
      throw new PropertyParseException("DAV:current-user-principal unavailable, server must support rfc5785.",
                                       getHostHREF(), WebDavConstants.PROPERTY_NAME_CURRENT_USER_PRINCIPAL);

    DavPropertyNameSet principalsProps = new DavPropertyNameSet();
    principalsProps.add(CardDavConstants.PROPERTY_NAME_ADDRESSBOOK_HOME_SET);
    principalsProps.add(DavPropertyName.DISPLAYNAME);

    String         propFindUri    = getHostHREF().concat(getCurrentUserPrincipal().get());
    PropFindMethod propFindMethod = new PropFindMethod(propFindUri,
                                                       principalsProps,
                                                       PropFindMethod.DEPTH_0);

    try {

      getClient().execute(propFindMethod);

      MultiStatus           multiStatus = propFindMethod.getResponseBodyAsMultiStatus();
      MultiStatusResponse[] msResponses = multiStatus.getResponses();

      for (MultiStatusResponse msResponse : msResponses) {
        DavPropertySet foundProperties = msResponse.getProperties(DavServletResponse.SC_OK);
        DavProperty    homeSetProperty = foundProperties.get(CardDavConstants.PROPERTY_NAME_ADDRESSBOOK_HOME_SET);

        for (Status status : msResponse.getStatus()) {
          if (status.getStatusCode() == DavServletResponse.SC_OK) {

            if (homeSetProperty != null && homeSetProperty.getValue() instanceof ArrayList) {
              for (Object child : (ArrayList<?>) homeSetProperty.getValue()) {
                if (child instanceof Element) {
                  String addressbookHomeSetUri = ((Element) child).getTextContent();
                  if (!(addressbookHomeSetUri.endsWith("/")))
                    addressbookHomeSetUri = addressbookHomeSetUri.concat("/");

                  addressBookHomeSet = Optional.of(addressbookHomeSetUri);
                  return addressBookHomeSet;
                }
              }
            }

            // OwnCloud :(
            else if (homeSetProperty != null && homeSetProperty.getValue() instanceof Element) {
              String addressbookHomeSetUri = ((Element) homeSetProperty.getValue()).getTextContent();
              if (!(addressbookHomeSetUri.endsWith("/")))
                addressbookHomeSetUri = addressbookHomeSetUri.concat("/");

              addressBookHomeSet = Optional.of(addressbookHomeSetUri);
              return addressBookHomeSet;
            }
          }
        }
      }

    } finally {
      propFindMethod.releaseConnection();
    }

    return Optional.absent();
  }

  @Override
  public void addCollection(String path)
      throws DavException, IOException
  {
    addCollection(path, new DavPropertySet());
  }

  public void addCollection(String path, DavPropertySet properties)
      throws DavException, IOException
  {
    ArrayList<DavPropertyName> resourceTypes = new ArrayList<DavPropertyName>();
    resourceTypes.add(DavPropertyName.create(DavConstants.XML_COLLECTION, DavConstants.NAMESPACE));
    resourceTypes.add(DavPropertyName.create("addressbook", CardDavConstants.CARDDAV_NAMESPACE)); // TODO: constant for this...
    properties.add(new DefaultDavProperty<ArrayList<DavPropertyName>>(DavPropertyName.RESOURCETYPE, resourceTypes));

    CardDavCollection collection    = new CardDavCollection(this, getHostHREF().concat(path), properties);
    MkColMethod       mkColMethod   = new MkColMethod(collection.getPath());
    ExtendedMkCol     extendedMkCol = new ExtendedMkCol(properties);

    try {

      mkColMethod.setRequestBody(extendedMkCol);
      getClient().execute(mkColMethod);

      if (!mkColMethod.succeeded())
        throw new DavException(mkColMethod.getStatusCode(), mkColMethod.getStatusText());

    } finally {
      mkColMethod.releaseConnection();
    }
  }

  public void addCollection(String path,
                            String displayName,
                            String description)
      throws DavException, IOException
  {
    DavPropertySet properties = new DavPropertySet();
    properties.add(new DefaultDavProperty<String>(DavPropertyName.DISPLAYNAME,                 displayName));
    properties.add(new DefaultDavProperty<String>(CardDavConstants.PROPERTY_NAME_ADDRESSBOOK_DESCRIPTION, description));
    addCollection(path, properties);
  }

  // TODO: I don't like this...
  public static List<CardDavCollection> getCollectionsFromMultiStatusResponses(CardDavStore          store,
                                                                               MultiStatusResponse[] msResponses)
  {
    List<CardDavCollection> collections = new LinkedList<CardDavCollection>();

    for (MultiStatusResponse msResponse : msResponses) {
      DavPropertySet foundProperties = msResponse.getProperties(DavServletResponse.SC_OK);
      String         collectionUri   = msResponse.getHref();

      for (Status status : msResponse.getStatus()) {
        if (status.getStatusCode() == DavServletResponse.SC_OK) {

          boolean        isAddressbookCollection = false;
          DavPropertySet collectionProperties    = new DavPropertySet();

          DavProperty resourceTypeProperty = foundProperties.get(DavPropertyName.RESOURCETYPE);
          if (resourceTypeProperty != null) {

            Object resourceTypeValue = resourceTypeProperty.getValue();
            if (resourceTypeValue instanceof ArrayList) {
              for (Node child : (ArrayList<Node>) resourceTypeValue) {
                if (child instanceof Element) {
                  String localName = child.getLocalName();
                  if (localName != null)
                    isAddressbookCollection = localName.equals(CardDavConstants.RESOURCE_TYPE_ADDRESSBOOK);
                }
              }
            }
          }

          if (isAddressbookCollection) {
            for (DavProperty property : foundProperties) {
              if (property != null)
                collectionProperties.add(property);
            }
            collections.add(new CardDavCollection(store, collectionUri, collectionProperties));
          }

        }
      }
    }

    return collections;
  }

  @Override
  public Optional<CardDavCollection> getCollection(String path) throws DavException, IOException {
    CardDavCollection  targetCollection = new CardDavCollection(this, path);
    DavPropertyNameSet collectionProps  = targetCollection.getPropertyNamesForFetch();
    PropFindMethod     propFindMethod   = new PropFindMethod(path, collectionProps, PropFindMethod.DEPTH_0);

    try {

      getClient().execute(propFindMethod);

      MultiStatus             multiStatus         = propFindMethod.getResponseBodyAsMultiStatus();
      MultiStatusResponse[]   responses           = multiStatus.getResponses();
      List<CardDavCollection> returnedCollections = getCollectionsFromMultiStatusResponses(this, responses);

      if (returnedCollections.size() == 0)
        Optional.absent();

      return Optional.of(returnedCollections.get(0));

    } catch (DavException e) {

      if (e.getErrorCode() == DavServletResponse.SC_NOT_FOUND)
        return Optional.absent();

      throw e;

    } finally {
      propFindMethod.releaseConnection();
    }
  }

  @Override
  public List<CardDavCollection> getCollections()
      throws PropertyParseException, DavException, IOException
  {
    Optional<String> addressbookHomeSetUri = getAddressbookHomeSet();
    if (!addressbookHomeSetUri.isPresent())
      throw new PropertyParseException("No addressbook-home-set property found for user.",
                                       getHostHREF(), CardDavConstants.PROPERTY_NAME_ADDRESSBOOK_HOME_SET);

    CardDavCollection  hack             = new CardDavCollection(this, "");
    DavPropertyNameSet addressbookProps = hack.getPropertyNamesForFetch();

    PropFindMethod method = new PropFindMethod(getHostHREF().concat(addressbookHomeSetUri.get()),
                                               addressbookProps,
                                               PropFindMethod.DEPTH_1);

    try {

      getClient().execute(method);

      MultiStatus           multiStatus = method.getResponseBodyAsMultiStatus();
      MultiStatusResponse[] responses   = multiStatus.getResponses();

      return getCollectionsFromMultiStatusResponses(this, responses);

    } finally {
      method.releaseConnection();
    }
  }

  @Override
  public void removeCollection(String path) throws DavException, IOException {
    DeleteMethod deleteMethod = new DeleteMethod(getHostHREF().concat(path));

    try {

      getClient().execute(deleteMethod);

      if (!deleteMethod.succeeded())
        throw new DavException(deleteMethod.getStatusCode(), deleteMethod.getStatusText());

    } finally {
      deleteMethod.releaseConnection();
    }
  }
}
