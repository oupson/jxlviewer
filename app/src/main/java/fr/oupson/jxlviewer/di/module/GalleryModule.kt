package fr.oupson.jxlviewer.di.module

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import fr.oupson.jxlviewer.repository.MediaStoreRepository
import fr.oupson.jxlviewer.repository.impl.MediaStoreRepositoryImpl

@Suppress("unused")
@Module
@InstallIn(ViewModelComponent::class)
abstract class GalleryModule {
    @Binds
    abstract fun bindMediaStoreRepository(mediaStoreRepositoryImp: MediaStoreRepositoryImpl): MediaStoreRepository
}
