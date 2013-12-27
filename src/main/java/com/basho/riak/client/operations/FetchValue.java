/*
 * Copyright 2013 Basho Technologies Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.basho.riak.client.operations;

import com.basho.riak.client.cap.Quorum;
import com.basho.riak.client.cap.VClock;
import com.basho.riak.client.convert.Converter;
import com.basho.riak.client.convert.PassThroughConverter;
import com.basho.riak.client.core.RiakCluster;
import com.basho.riak.client.core.operations.FetchOperation;
import com.basho.riak.client.query.RiakObject;
import com.basho.riak.client.util.ByteArrayWrapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.basho.riak.client.convert.Converters.convert;
import java.util.EnumMap;

/**
 * Command used to fetch a value from Riak, referenced by it's key.
 */
public class FetchValue<T> extends RiakCommand<FetchValue.Response<T>>
{

    private final Location location;
    private final EnumMap<FetchOption.Type, Object> options;
    private final Converter<T> converter;

    FetchValue(Location location, Converter<T> converter)
    {
        this.options = new EnumMap<FetchOption.Type, Object>(FetchOption.Type.class);
        this.converter = converter;
        this.location = location;
    }

    /**
     * Factory method for a FetchValue command with a conversion step
     *
     * @param location  the key to fetch
     * @param converter a domain object converter
     * @param <T>       the type of the domain object
     * @return a response object
     */
    public static <T> FetchValue<T> fetch(Key location, Converter<T> converter)
    {
        return new FetchValue(location, converter);
    }

    /**
     * Factory method for a FetchValue command returning raw RiakObjects
     *
     * @param location the key to fetch
     * @return a response object
     */
    public static FetchValue<RiakObject> fetch(Key location)
    {
        return new FetchValue<RiakObject>(location, new PassThroughConverter());
    }

    /**
     * Add an optional setting for this command. This will be passed along with the
     * request to Riak to tell it how to behave when servicing the request.
     *
     * @param option
     * @param value
     * @param <U>
     * @return
     */
    public <U> FetchValue<T> withOption(FetchOption<U> option, U value)
    {
        options.put(option.getType(), value);
        return this;
    }

    @Override
    Response<T> execute(RiakCluster cluster) throws ExecutionException, InterruptedException
    {

        ByteArrayWrapper type = location.getType();
        ByteArrayWrapper bucket = location.getBucket();
        ByteArrayWrapper key = location.getKey();

        FetchOperation.Builder builder = new FetchOperation.Builder(bucket, key);
        builder.withBucketType(type);

        for (Map.Entry<FetchOption.Type, Object> opPair : options.entrySet())
        {

            FetchOption.Type option = opPair.getKey();

            switch(option)
            {
                case R:
                    builder.withR(((Quorum) opPair.getValue()).getIntValue());
                    break;
                case DELETED_VCLOCK:
                    builder.withReturnDeletedVClock((Boolean) opPair.getValue());
                    break;
                case TIMEOUT:
                    builder.withTimeout((Integer) opPair.getValue());
                    break;
                case HEAD:
                    builder.withHeadOnly((Boolean) opPair.getValue());
                    break;
                case BASIC_QUORUM:
                    builder.withBasicQuorum((Boolean) opPair.getValue());
                    break;
                case IF_MODIFIED:
                    VClock clock = (VClock) opPair.getValue();
                    builder.withIfNotModified(clock.getBytes());
                    break;
                case N_VAL:
                    builder.withNVal((Integer) opPair.getValue());
                    break;
                case PR:
                    builder.withPr(((Quorum) opPair.getValue()).getIntValue());
                    break;
                case SLOPPY_QUORUM:
                    builder.withSloppyQuorum((Boolean) opPair.getValue());
                    break;
                case NOTFOUND_OK:
                    builder.withNotFoundOK((Boolean) opPair.getValue());
            }
        }

        FetchOperation operation = builder.build();

        FetchOperation.Response response = cluster.execute(operation).get();
        List<T> converted = convert(converter, response.getObjectList());

        return new Response<T>(response.isNotFound(), response.isUnchanged(), converted, response.getVClock());

    }

    /**
     * A response from Riak including the vector clock.
     *
     * @param <T> the type of the returned object, if no converter given this will be RiakObject
     */
    public static class Response<T>
    {

        private final boolean notFound;
        private final boolean unchanged;
        private final VClock vClock;
        private final List<T> value;

        Response(boolean notFound, boolean unchanged, List<T> value, VClock vClock)
        {
            this.notFound = notFound;
            this.unchanged = unchanged;
            this.value = value;
            this.vClock = vClock;
        }

        public boolean isNotFound()
        {
            return notFound;
        }

        public boolean isUnchanged()
        {
            return unchanged;
        }

        public boolean hasvClock()
        {
            return vClock != null;
        }

        public VClock getvClock()
        {
            return vClock;
        }

        public boolean hasValue()
        {
            return value != null;
        }

        public List<T> getValue()
        {
            return value;
        }

    }
}
