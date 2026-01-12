namespace ImageSearch.Api.Exceptions;

public class SearchServiceUnavailableException : Exception
{
    public SearchServiceUnavailableException(string message) : base(message) { }
}
