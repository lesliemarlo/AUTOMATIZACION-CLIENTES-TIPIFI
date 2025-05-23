package com.informaperu.cliente.model;

import java.util.List;

public class ResponseClienteWrapper {
    private Integer count;
    private String next;
    private String previous;
    private List<ClienteDTO> results;

    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
    public String getNext() { return next; }
    public void setNext(String next) { this.next = next; }
    public String getPrevious() { return previous; }
    public void setPrevious(String previous) { this.previous = previous; }
    public List<ClienteDTO> getResults() { return results; }
    public void setResults(List<ClienteDTO> result) { this.results = result; }
}
