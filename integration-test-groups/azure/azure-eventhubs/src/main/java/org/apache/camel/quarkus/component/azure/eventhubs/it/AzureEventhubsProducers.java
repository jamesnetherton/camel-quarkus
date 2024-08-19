package org.apache.camel.quarkus.component.azure.eventhubs.it;

import java.util.Optional;

import com.azure.messaging.eventhubs.CheckpointStore;
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;

public class AzureEventhubsProducers {
    @ConfigProperty(name = "azure.storage.account-name")
    String azureStorageAccountName;

    @ConfigProperty(name = "azure.storage.account-key")
    String azureStorageAccountKey;

    @ConfigProperty(name = "azure.event.hubs.connection-string")
    Optional<String> connectionString;

    @ConfigProperty(name = "azure.blob.container.name")
    Optional<String> azureBlobContainerName;

    @ConfigProperty(name = "azure.blob.service.url")
    Optional<String> azureBlobServiceUrl;

    @Named("foo")
    CheckpointStore checkpointStore(BlobContainerAsyncClient client) {
        return new BlobCheckpointStore(client);
    }

    @Named("client")
    BlobContainerAsyncClient client() {
        return new BlobContainerClientBuilder()
                .endpoint(azureBlobServiceUrl.get())
                .containerName(azureBlobContainerName.get())
                .credential(new StorageSharedKeyCredential(azureStorageAccountName, azureStorageAccountKey))
                .buildAsyncClient();
    }
}
