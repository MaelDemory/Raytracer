//
// Projet POO Ray Tracing - pont natif Metal
//
// (c) 2025 Maël DEMORY
//
// Expose une petite API C au-dessus de Metal. Le côté Java l'appelle via la
// Foreign Function & Memory API, sans JNI ni en-tête généré.
//
// Compilation : voir src/main/native/build.sh
//

#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#include <stdint.h>
#include <string.h>

/// Codes de retour.
enum {
    RTM_OK = 0,
    RTM_ERR_ARGUMENT = 1,
    RTM_ERR_DEVICE = 2,
    RTM_ERR_DISPATCH = 3
};

/// Nombre de lignes rendues par command buffer. Découper le rendu évite de
/// présenter au GPU une commande assez longue pour déclencher le chien de garde.
static const NSUInteger RTM_BAND_ROWS = 64;

/// Contexte Metal conservé entre les appels.
@interface RTMContext : NSObject
@property (nonatomic, strong) id<MTLDevice> device;
@property (nonatomic, strong) id<MTLCommandQueue> queue;
@property (nonatomic, strong) id<MTLComputePipelineState> pipeline;
@property (nonatomic, assign) BOOL supportsNonUniformThreadgroups;
@end

@implementation RTMContext
@end

/// Duplique un message d'erreur pour le côté appelant.
static void rtm_set_error(char **out, NSString *message) {
    if (out == NULL) {
        return;
    }
    *out = strdup(message.UTF8String ?: "erreur Metal inconnue");
}

/// Libère une chaîne renvoyée par la bibliothèque.
void rtm_free_string(char *text) {
    free(text);
}

/// Indique si un périphérique Metal est utilisable sur cette machine.
int rtm_available(void) {
    @autoreleasepool {
        id<MTLDevice> device = MTLCreateSystemDefaultDevice();
        return device != nil ? 1 : 0;
    }
}

/// Écrit le nom du périphérique Metal par défaut dans le tampon fourni.
/// Retourne la longueur écrite, ou 0 si aucun périphérique n'est disponible.
int rtm_default_device_name(char *buffer, int capacity) {
    @autoreleasepool {
        if (buffer == NULL || capacity <= 0) {
            return 0;
        }
        id<MTLDevice> device = MTLCreateSystemDefaultDevice();
        if (device == nil) {
            return 0;
        }
        const char *name = device.name.UTF8String ?: "GPU Metal";
        strncpy(buffer, name, (size_t) capacity - 1);
        buffer[capacity - 1] = '\0';
        return (int) strlen(buffer);
    }
}

/// Crée un contexte : périphérique, file de commandes et pipeline compilé depuis
/// la source Metal Shading Language fournie.
/// @return Poignée opaque, ou NULL en cas d'échec (errOut reçoit le message).
void *rtm_create(const char *shaderSource, char **errOut) {
    @autoreleasepool {
        if (shaderSource == NULL) {
            rtm_set_error(errOut, @"source du shader absente");
            return NULL;
        }

        id<MTLDevice> device = MTLCreateSystemDefaultDevice();
        if (device == nil) {
            rtm_set_error(errOut, @"aucun périphérique Metal disponible");
            return NULL;
        }

        NSError *error = nil;
        // Mathématiques strictes : le mode rapide réassocie les expressions et
        // ferait diverger le rendu de la référence CPU.
        MTLCompileOptions *options = [MTLCompileOptions new];
        if (@available(macOS 15.0, *)) {
            options.mathMode = MTLMathModeSafe;
        } else {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
            options.fastMathEnabled = NO;
#pragma clang diagnostic pop
        }

        NSString *source = [NSString stringWithUTF8String:shaderSource];
        id<MTLLibrary> library = [device newLibraryWithSource:source
                                                     options:options
                                                       error:&error];
        if (library == nil) {
            rtm_set_error(errOut, [NSString stringWithFormat:@"compilation du shader: %@",
                                                             error.localizedDescription]);
            return NULL;
        }

        id<MTLFunction> function = [library newFunctionWithName:@"trace"];
        if (function == nil) {
            rtm_set_error(errOut, @"fonction 'trace' introuvable dans le shader");
            return NULL;
        }

        id<MTLComputePipelineState> pipeline =
            [device newComputePipelineStateWithFunction:function error:&error];
        if (pipeline == nil) {
            rtm_set_error(errOut, [NSString stringWithFormat:@"création du pipeline: %@",
                                                             error.localizedDescription]);
            return NULL;
        }

        id<MTLCommandQueue> queue = [device newCommandQueue];
        if (queue == nil) {
            rtm_set_error(errOut, @"création de la file de commandes impossible");
            return NULL;
        }

        RTMContext *context = [RTMContext new];
        context.device = device;
        context.queue = queue;
        context.pipeline = pipeline;
        context.supportsNonUniformThreadgroups =
            [device supportsFamily:MTLGPUFamilyApple4];

        return (__bridge_retained void *) context;
    }
}

/// Détruit un contexte créé par rtm_create.
void rtm_release(void *handle) {
    if (handle == NULL) {
        return;
    }
    @autoreleasepool {
        RTMContext *context = (__bridge_transfer RTMContext *) handle;
        context = nil;
    }
}

/// Écrit le nom du périphérique associé au contexte.
int rtm_context_device_name(void *handle, char *buffer, int capacity) {
    if (handle == NULL || buffer == NULL || capacity <= 0) {
        return 0;
    }
    @autoreleasepool {
        RTMContext *context = (__bridge RTMContext *) handle;
        const char *name = context.device.name.UTF8String ?: "GPU Metal";
        strncpy(buffer, name, (size_t) capacity - 1);
        buffer[capacity - 1] = '\0';
        return (int) strlen(buffer);
    }
}

/// Lance le rendu complet d'une image.
///
/// @param handle      contexte créé par rtm_create
/// @param params      bloc de paramètres uniformes (disposition définie côté Java)
/// @param paramsLen   taille du bloc en octets
/// @param floats      concaténation des tableaux flottants de la scène
/// @param floatCount  nombre de flottants
/// @param nodes       topologie du BVH, deux entiers par noeud
/// @param nodeIntCount nombre d'entiers
/// @param out         tampon de sortie, width * height * 3 flottants RGB
/// @param outCount    nombre de flottants attendus en sortie
/// @param width       largeur de l'image
/// @param height      hauteur de l'image
/// @param errOut      reçoit le message d'erreur en cas d'échec
/// @return RTM_OK, ou un code d'erreur
int rtm_render(void *handle,
               const void *params, uint32_t paramsLen,
               const float *floats, uint32_t floatCount,
               const int32_t *nodes, uint32_t nodeIntCount,
               float *out, uint32_t outCount,
               uint32_t width, uint32_t height,
               char **errOut) {
    @autoreleasepool {
        if (handle == NULL || params == NULL || floats == NULL || out == NULL) {
            rtm_set_error(errOut, @"argument nul");
            return RTM_ERR_ARGUMENT;
        }
        if (width == 0 || height == 0 || outCount != width * height * 3) {
            rtm_set_error(errOut, @"dimensions incohérentes avec le tampon de sortie");
            return RTM_ERR_ARGUMENT;
        }

        RTMContext *context = (__bridge RTMContext *) handle;
        id<MTLDevice> device = context.device;

        const MTLResourceOptions shared = MTLResourceStorageModeShared;

        id<MTLBuffer> paramsBuffer = [device newBufferWithBytes:params
                                                         length:paramsLen
                                                        options:shared];
        id<MTLBuffer> floatBuffer = [device newBufferWithBytes:floats
                                                        length:floatCount * sizeof(float)
                                                       options:shared];
        // Une scène sans forme bornée n'a aucun noeud : Metal refuse une taille nulle.
        int32_t emptyNode = 0;
        id<MTLBuffer> nodeBuffer = (nodeIntCount > 0)
            ? [device newBufferWithBytes:nodes
                                  length:nodeIntCount * sizeof(int32_t)
                                 options:shared]
            : [device newBufferWithBytes:&emptyNode length:sizeof(int32_t) options:shared];
        id<MTLBuffer> outBuffer = [device newBufferWithLength:outCount * sizeof(float)
                                                      options:shared];

        if (paramsBuffer == nil || floatBuffer == nil || nodeBuffer == nil || outBuffer == nil) {
            rtm_set_error(errOut, @"allocation d'un tampon Metal impossible");
            return RTM_ERR_DEVICE;
        }

        NSUInteger tgWidth = context.pipeline.threadExecutionWidth;
        NSUInteger tgHeight = context.pipeline.maxTotalThreadsPerThreadgroup / tgWidth;
        if (tgHeight == 0) {
            tgHeight = 1;
        }
        MTLSize threadgroup = MTLSizeMake(tgWidth, tgHeight, 1);

        for (uint32_t firstRow = 0; firstRow < height; firstRow += RTM_BAND_ROWS) {
            uint32_t rows = MIN((uint32_t) RTM_BAND_ROWS, height - firstRow);
            int32_t rowOffset = (int32_t) firstRow;

            id<MTLCommandBuffer> commands = [context.queue commandBuffer];
            id<MTLComputeCommandEncoder> encoder = [commands computeCommandEncoder];
            if (commands == nil || encoder == nil) {
                rtm_set_error(errOut, @"encodage de la commande impossible");
                return RTM_ERR_DISPATCH;
            }

            [encoder setComputePipelineState:context.pipeline];
            [encoder setBuffer:paramsBuffer offset:0 atIndex:0];
            [encoder setBuffer:floatBuffer offset:0 atIndex:1];
            [encoder setBuffer:nodeBuffer offset:0 atIndex:2];
            [encoder setBuffer:outBuffer offset:0 atIndex:3];
            [encoder setBytes:&rowOffset length:sizeof(int32_t) atIndex:4];

            if (context.supportsNonUniformThreadgroups) {
                [encoder dispatchThreads:MTLSizeMake(width, rows, 1)
                   threadsPerThreadgroup:threadgroup];
            } else {
                MTLSize groups = MTLSizeMake((width + tgWidth - 1) / tgWidth,
                                             (rows + tgHeight - 1) / tgHeight,
                                             1);
                [encoder dispatchThreadgroups:groups threadsPerThreadgroup:threadgroup];
            }

            [encoder endEncoding];
            [commands commit];
            [commands waitUntilCompleted];

            if (commands.error != nil) {
                rtm_set_error(errOut, [NSString stringWithFormat:@"exécution du kernel: %@",
                                                                 commands.error.localizedDescription]);
                return RTM_ERR_DISPATCH;
            }
        }

        memcpy(out, outBuffer.contents, outCount * sizeof(float));
        return RTM_OK;
    }
}
