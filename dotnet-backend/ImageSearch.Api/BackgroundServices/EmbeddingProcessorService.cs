using System.Threading.Channels;
using ImageSearch.Api.Clients;
using ImageSearch.Api.Models.SearchService;
using ImageSearch.Api.Services;

namespace ImageSearch.Api.BackgroundServices;

public class EmbeddingProcessorService : BackgroundService
{
    private readonly IServiceProvider _serviceProvider;
    private readonly Channel<EmbeddingTask> _queue;
    private readonly ILogger<EmbeddingProcessorService> _logger;

    public EmbeddingProcessorService(
        IServiceProvider serviceProvider,
        Channel<EmbeddingTask> queue,
        ILogger<EmbeddingProcessorService> logger)
    {
        _serviceProvider = serviceProvider;
        _queue = queue;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        _logger.LogInformation("Embedding processor service started");

        await foreach (var task in _queue.Reader.ReadAllAsync(stoppingToken))
        {
            try
            {
                using var scope = _serviceProvider.CreateScope();
                var searchClient = scope.ServiceProvider.GetRequiredService<IPythonSearchClient>();

                // Process in batches of 32
                const int batchSize = 32;
                for (int i = 0; i < task.Images.Count; i += batchSize)
                {
                    var batch = task.Images.Skip(i).Take(batchSize).ToList();
                    var request = new EmbedImagesRequest(task.UserId, task.FolderId, batch);

                    await searchClient.EmbedImagesAsync(request);
                    _logger.LogInformation("Processed embedding batch {Batch} for folder {FolderId}",
                        i / batchSize + 1, task.FolderId);

                    await Task.Delay(1000, stoppingToken); // Throttle between batches
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to process embedding task for folder {FolderId}", task.FolderId);
            }
        }
    }
}
