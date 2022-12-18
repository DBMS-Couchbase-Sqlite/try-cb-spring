/**
 * Copyright (C) 2021 Couchbase, Inc.
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALING
 * IN THE SOFTWARE.
 */

package trycb.repository;

import org.springframework.data.couchbase.repository.Collection;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.DynamicProxyable;
import org.springframework.data.couchbase.repository.ScanConsistency;
import org.springframework.data.couchbase.repository.Scope;
import org.springframework.stereotype.Repository;

import com.couchbase.client.java.query.QueryScanConsistency;
import trycb.config.Hotel;

/**
 * Hotel repository<br>
 * This is used only to obtain the underlying Couchbase Java SDK cluster and bucket objects.
 *
 * @author Michael Reiche
 */
@Repository("hotelRepository")
@Scope("inventory")
@Collection("hotel")
@ScanConsistency(query = QueryScanConsistency.REQUEST_PLUS)
public interface HotelRepository extends CouchbaseRepository<Hotel, String>, DynamicProxyable<HotelRepository> {

}