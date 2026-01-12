using System.Threading.Channels;
using ImageSearch.Api.BackgroundServices;
using ImageSearch.Api.Clients;
using ImageSearch.Api.Data;
using ImageSearch.Api.Middleware;
using ImageSearch.Api.Services;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.FileProviders;
using Newtonsoft.Json.Serialization;

var builder = WebApplication.CreateBuilder(args);

// Database Configuration (PostgreSQL with EF Core)
var connectionString = builder.Configuration.GetConnectionString("DefaultConnection");
builder.Services.AddDbContext<ApplicationDbContext>(options =>
    options.UseNpgsql(connectionString, npgsqlOptions =>
        npgsqlOptions.EnableRetryOnFailure(maxRetryCount: 3)));

// JSON Serialization - snake_case to match Java/Python backends
builder.Services.AddControllers()
    .AddNewtonsoftJson(options =>
    {
        options.SerializerSettings.ContractResolver = new DefaultContractResolver
        {
            NamingStrategy = new SnakeCaseNamingStrategy()
        };
    });

// CORS - allow React frontend
builder.Services.AddCors(options =>
{
    options.AddPolicy("AllowFrontend", policy =>
    {
        policy.WithOrigins("http://localhost:3000", "http://localhost:3001")
              .AllowAnyMethod()
              .AllowAnyHeader()
              .AllowCredentials();
    });
});

// Register services
builder.Services.AddScoped<ISessionService, SessionService>();
builder.Services.AddScoped<IUserService, UserService>();
builder.Services.AddScoped<IFolderService, FolderService>();
builder.Services.AddScoped<IImageService, ImageService>();
builder.Services.AddScoped<ISearchService, SearchService>();

// HTTP Client for Python search service with retry policy
builder.Services.AddHttpClient<IPythonSearchClient, PythonSearchClient>(client =>
{
    var baseUrl = builder.Configuration["SearchService:BaseUrl"] ?? "http://localhost:5000";
    client.BaseAddress = new Uri(baseUrl);
    client.Timeout = TimeSpan.FromSeconds(120);
})
.AddTransientHttpErrorPolicy(policy =>
    policy.WaitAndRetryAsync(3, retryAttempt => TimeSpan.FromSeconds(Math.Pow(2, retryAttempt))));

// Channel for background embedding processing
builder.Services.AddSingleton(Channel.CreateUnbounded<EmbeddingTask>());

// Background Services
builder.Services.AddHostedService<EmbeddingProcessorService>();
builder.Services.AddHostedService<SessionCleanupService>();

// Swagger for API documentation
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

// Configure Kestrel to listen on port 7000
builder.WebHost.UseUrls("http://localhost:7000");

var app = builder.Build();

// Middleware Pipeline
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseCors("AllowFrontend");
app.UseMiddleware<GlobalExceptionMiddleware>();

// Static file serving for images
var projectRoot = Directory.GetParent(Directory.GetCurrentDirectory())?.FullName ?? Directory.GetCurrentDirectory();
var imagesPath = Path.Combine(projectRoot, "data", "uploads", "images");
if (!Directory.Exists(imagesPath))
{
    Directory.CreateDirectory(imagesPath);
}

app.UseStaticFiles(new StaticFileOptions
{
    FileProvider = new PhysicalFileProvider(imagesPath),
    RequestPath = "/images"
});

app.MapControllers();

// Auto-migrate database on startup (development only)
if (app.Environment.IsDevelopment())
{
    using var scope = app.Services.CreateScope();
    var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
    try
    {
        // Don't migrate - use existing database
        var canConnect = await dbContext.Database.CanConnectAsync();
        if (canConnect)
        {
            app.Logger.LogInformation("Successfully connected to database");
        }
        else
        {
            app.Logger.LogWarning("Cannot connect to database");
        }
    }
    catch (Exception ex)
    {
        app.Logger.LogError(ex, "Error connecting to database");
    }
}

app.Logger.LogInformation("Starting .NET backend on http://localhost:7000");
app.Run();
