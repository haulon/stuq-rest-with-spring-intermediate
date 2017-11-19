package io.junq.examples.usercenter.client.template;

import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import io.junq.examples.client.marshall.IMarshaller;
import io.junq.examples.common.interfaces.IDto;
import io.junq.examples.common.spring.util.Profiles;
import io.junq.examples.common.util.QueryConstants;
import io.junq.examples.common.web.WebConstants;
import io.junq.examples.usercenter.client.UserCenterPaths;
import io.junq.examples.usercenter.util.UserCenter;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

@Component
@Profile(Profiles.CLIENT)
public abstract class GenericSimpleApiClient<T extends IDto> {

	private final static String JSON = MediaType.APPLICATION_JSON.toString();

	@Autowired
	protected UserCenterPaths paths;

	@Autowired
	private IMarshaller marshaller;

	private final Class<T> clazz;

	public GenericSimpleApiClient(final Class<T> clazz) {
		this.clazz = clazz;
	}

	// API

	// find - one

	public final T findOne(final long id) {
		final String uriOfResource = getUri() + WebConstants.PATH_SEP + id;
		return findOneByUri(uriOfResource);
	}

	public final Response findOneAsResponse(final long id) {
		final String uriOfResource = getUri() + WebConstants.PATH_SEP + id;
		return findByUriAsResponse(uriOfResource);
	}

	private final T findOneByUri(final String uriOfResource) {
		String resourceAsMime = findOneByUriAsString(uriOfResource);
		return marshaller.decode(resourceAsMime, clazz);
	}

	private final String findOneByUriAsString(final String uriOfResource) {
		final Response response = read(uriOfResource);
		Preconditions.checkState(response.getStatusCode() == 200);

		return response.asString();
	}

	public final Response findByUriAsResponse(final String uriOfResource) {
		return read(uriOfResource);
	}

	// find - all (sorted, paginated)

	public final List<T> findAllSorted(final String sortBy, final String sortOrder) {
		final Response findAllResponse = findByUriAsResponse(
				getUri() + QueryConstants.Q_SORT_BY + sortBy + QueryConstants.S_ORDER + sortOrder);
		return marshaller.<T>decodeList(findAllResponse.getBody().asString(), clazz);
	}

	public final List<T> findAllPaginated(final int page, final int size) {
		final Response allPaginatedAsResponse = findAllPaginatedAsResponse(page, size);
		return marshaller.<T>decodeList(allPaginatedAsResponse.getBody().asString(), clazz);
	}

	public final List<T> findAllPaginatedAndSorted(final int page, final int size, final String sortBy,
			final String sortOrder) {
		final Response allPaginatedAndSortedAsResponse = findAllPaginatedAndSortedAsResponse(page, size, sortBy,
				sortOrder);
		return marshaller.<T>decodeList(allPaginatedAndSortedAsResponse.getBody().asString(), clazz);
	}

	public final Response findAllPaginatedAndSortedAsResponse(final int page, final int size, final String sortBy,
			final String sortOrder) {
		final StringBuilder uri = new StringBuilder(getUri());
		uri.append(QueryConstants.QUESTIONMARK);
		uri.append("page=");
		uri.append(page);
		uri.append(QueryConstants.SEPARATOR_AMPER);
		uri.append("size=");
		uri.append(size);
		Preconditions.checkState(!(sortBy == null && sortOrder != null));
		if (sortBy != null) {
			uri.append(QueryConstants.SEPARATOR_AMPER);
			uri.append(QueryConstants.SORT_BY + "=");
			uri.append(sortBy);
		}
		if (sortOrder != null) {
			uri.append(QueryConstants.SEPARATOR_AMPER);
			uri.append(QueryConstants.SORT_ORDER + "=");
			uri.append(sortOrder);
		}

		return read(uri.toString());
	}

	public final Response findAllSortedAsResponse(final String sortBy, final String sortOrder) {
		final StringBuilder uri = new StringBuilder(getUri());
		uri.append(QueryConstants.QUESTIONMARK);
		Preconditions.checkState(!(sortBy == null && sortOrder != null));
		if (sortBy != null) {
			uri.append(QueryConstants.SORT_BY + "=");
			uri.append(sortBy);
		}
		if (sortOrder != null) {
			uri.append(QueryConstants.SEPARATOR_AMPER);
			uri.append(QueryConstants.SORT_ORDER + "=");
			uri.append(sortOrder);
		}

		return read(uri.toString());
	}

	public final Response findAllPaginatedAsResponse(final int page, final int size) {
		final StringBuilder uri = new StringBuilder(getUri());
		uri.append(QueryConstants.QUESTIONMARK);
		uri.append("page=");
		uri.append(page);
		uri.append(QueryConstants.SEPARATOR_AMPER);
		uri.append("size=");
		uri.append(size);
		return read(uri.toString());
	}

	// count

	public final Response countAsResponse() {
		return read(getUri() + "/count");
	}

	// create

	public final T create(final T resource) {

		final String uriForResourceCreation = createAsUri(resource);
		final String resourceAsMime = findOneByUriAsString(uriForResourceCreation);
		return marshaller.decode(resourceAsMime, clazz);
	}

	public final String createAsUri(final T resource) {
		final Response response = createAsResponse(resource);
		Preconditions.checkState(response.getStatusCode() == 201, "create operation: " + response.getStatusCode());

		final String locationOfCreatedResource = response.getHeader(HttpHeaders.LOCATION);
		Preconditions.checkNotNull(locationOfCreatedResource);
		return locationOfCreatedResource;
	}

	public final Response createAsResponse(final T resource) {
		Preconditions.checkNotNull(resource);
		final RequestSpecification givenAuthenticated = givenAuthenticated();

		return givenAuthenticated.contentType(JSON).body(resource).post(getUri());
	}

	// update

	public final void update(final T resource) {
		final Response updateResponse = updateAsResponse(resource);
		Preconditions.checkState(updateResponse.getStatusCode() == 200,
				"Update Operation: " + updateResponse.getStatusCode());
	}

	public final Response updateAsResponse(final T resource) {
		Preconditions.checkNotNull(resource);

		return givenAuthenticated().contentType(JSON).body(resource).put(getUri() + "/" + resource.getId());
	}

	// delete

	public final void delete(final long id) {
		final Response deleteResponse = deleteAsResponse(id);
		Preconditions.checkState(deleteResponse.getStatusCode() == 204);
	}

	public final Response deleteAsResponse(final long id) {
		return givenAuthenticated().delete(getUri() + WebConstants.PATH_SEP + id);
	}

	// API - other

	public abstract String getUri();

	public final RequestSpecification givenAuthenticated() {
		final Pair<String, String> credentials = getDefaultCredentials();
		return RestAssured.given().auth().preemptive().basic(credentials.getLeft(), credentials.getRight())
		// .log().all()
		;
	}

	public final Response read(final String uriOfResource) {
		return readRequest().get(uriOfResource);
	}

	// 工具方法

	private final RequestSpecification readRequest() {
		return readRequest(givenAuthenticated());
	}

	private final RequestSpecification readRequest(final RequestSpecification req) {
		return req.header(HttpHeaders.ACCEPT, JSON);
	}

	private final Pair<String, String> getDefaultCredentials() {
		return new ImmutablePair<String, String>(UserCenter.ADMIN_EMAIL, UserCenter.ADMIN_PASS);
	}

	public final IMarshaller getMarshaller() {
		return marshaller;
	}
}
