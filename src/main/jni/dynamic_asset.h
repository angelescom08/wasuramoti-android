#ifndef DYNAMIC_ASSET_H
#define DYNAMIC_ASSET_H

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

extern void * dynAssetHandle;
extern AAssetManager* (*DynAssetManager_fromJava)(JNIEnv* env, jobject assetManager);
extern AAsset* (*DynAssetManager_open)(AAssetManager* mgr, const char* filename, int mode);
extern void (*DynAsset_close)(AAsset* asset);
extern off_t (*DynAsset_getLength)(AAsset* asset);
extern off_t (*DynAsset_getRemainingLength)(AAsset* asset);
extern int (*DynAsset_read)(AAsset* asset, void* buf, size_t count);
extern off_t (*DynAsset_seek)(AAsset* asset, off_t offset, int whence);

#endif //DYNAMIC_ASSET_H
