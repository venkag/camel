/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dataformat.csv;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVStrategy;
import org.apache.commons.csv.writer.CSVConfig;
import org.apache.commons.csv.writer.CSVField;
import org.apache.commons.csv.writer.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CSV Data format.
 * <p/>
 * By default, columns are autogenerated in the resulting CSV. Subsequent
 * messages use the previously created columns with new fields being added at
 * the end of the line. Thus, field order is the same from message to message.
 * Autogeneration can be disabled. In this case, only the fields defined in
 * csvConfig are written on the output.
 *
 * @version 
 */
public class CsvDataFormat implements DataFormat {
    private static final Logger LOGGER = LoggerFactory.getLogger(CsvDataFormat.class);

    private CSVStrategy strategy = cloneCSVStrategyIfNecessary(CSVStrategy.DEFAULT_STRATEGY);
    private CSVConfig config = new CSVConfig();
    private boolean autogenColumns = true;
    private String delimiter;
    private boolean skipFirstLine;
    /**
     * Lazy row loading with iterator for big files.
     */
    private boolean lazyLoad;

    private static CSVStrategy cloneCSVStrategyIfNecessary(CSVStrategy csvStrategy) {
        for (Field field : CSVStrategy.class.getFields()) {
            try {
                if (field.get(null) == csvStrategy) {
                    // return a safe copy of the declared static constant so that we don't cause any side effect
                    // by (potentially) other CsvDataFormat objects in use, as we change the properties of the
                    // strategy itself (e.g. it's set delimiter through the #unmarshal() method below)
                    LOGGER.debug("Returning a clone of {} as it is the declared constant {} by the CSVStrategy class", csvStrategy, field.getName());

                    return (CSVStrategy) csvStrategy.clone();
                }
            } catch (Exception e) {
                ObjectHelper.wrapRuntimeCamelException(e);
            }
        }

        // not a declared static constant of CSVStrategy so return it as is
        return csvStrategy;
    }

    public void marshal(Exchange exchange, Object object, OutputStream outputStream) throws Exception {
        if (delimiter != null) {
            config.setDelimiter(delimiter.charAt(0));
        }

        OutputStreamWriter out = new OutputStreamWriter(outputStream, IOHelper.getCharsetName(exchange));
        CSVWriter csv = new CSVWriter(config);
        csv.setWriter(out);

        try {
            List<?> list = ExchangeHelper.convertToType(exchange, List.class, object);
            if (list != null) {
                for (Object child : list) {
                    Map<?, ?> row = ExchangeHelper.convertToMandatoryType(exchange, Map.class, child);
                    doMarshalRecord(exchange, row, out, csv);
                }
            } else {
                Map<?, ?> row = ExchangeHelper.convertToMandatoryType(exchange, Map.class, object);
                doMarshalRecord(exchange, row, out, csv);
            }
        } finally {
            IOHelper.close(out);
        }
    }

    private void doMarshalRecord(Exchange exchange, Map<?, ?> row, Writer out, CSVWriter csv) throws Exception {
        if (autogenColumns) {
            // no specific config has been set so lets add fields
            Set<?> set = row.keySet();
            updateFieldsInConfig(set, exchange);
        }
        csv.writeRecord(row);
    }

    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        if (delimiter != null) {
            config.setDelimiter(delimiter.charAt(0));
        }
        strategy.setDelimiter(config.getDelimiter());

        Reader reader = null;
        boolean error = false;
        try {
            reader = IOHelper.buffered(new InputStreamReader(inputStream, IOHelper.getCharsetName(exchange)));
            CSVParser parser = new CSVParser(reader, strategy);

            if (skipFirstLine) {
                // read one line ahead and skip it
                parser.getLine();
            }

            CsvIterator csvIterator = new CsvIterator(parser, reader);
            return lazyLoad ? csvIterator : loadAllAsList(csvIterator);
        } catch (Exception e) {
            error = true;
            throw e;
        } finally {
            if (error) {
                IOHelper.close(reader);
            }
        }
    }

    private List<List<String>> loadAllAsList(CsvIterator iter) {
        try {
            List<List<String>> list = new ArrayList<List<String>>();
            while (iter.hasNext()) {
                list.add(iter.next());
            }
            return list;
        } finally {
            // close the iterator (which would also close the reader) as we've loaded all the data upfront
            IOHelper.close(iter);
        }
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        if (delimiter != null && delimiter.length() > 1) {
            throw new IllegalArgumentException("Delimiter must have a length of one!");
        }
        this.delimiter = delimiter;
    }
    
    public CSVConfig getConfig() {
        return config;
    }

    public void setConfig(CSVConfig config) {
        this.config = config;
    }

    public CSVStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(CSVStrategy strategy) {
        this.strategy = cloneCSVStrategyIfNecessary(strategy);
    }

    public boolean isAutogenColumns() {
        return autogenColumns;
    }

    /**
     * Auto generate columns.
     *
     * @param autogenColumns set to false to disallow column autogeneration (default true)
     */
    public void setAutogenColumns(boolean autogenColumns) {
        this.autogenColumns = autogenColumns;
    }

    public boolean isSkipFirstLine() {
        return skipFirstLine;
    }

    public void setSkipFirstLine(boolean skipFirstLine) {
        this.skipFirstLine = skipFirstLine;
    }

    public boolean isLazyLoad() {
        return lazyLoad;
    }

    public void setLazyLoad(boolean lazyLoad) {
        this.lazyLoad = lazyLoad;
    }

    private synchronized void updateFieldsInConfig(Set<?> set, Exchange exchange) {
        for (Object value : set) {
            if (value != null) {
                String text = exchange.getContext().getTypeConverter().convertTo(String.class, value);
                // do not add field twice
                if (config.getField(text) == null) {
                    CSVField field = new CSVField(text);
                    config.addField(field);
                }
            }
        }
    }
    
}
