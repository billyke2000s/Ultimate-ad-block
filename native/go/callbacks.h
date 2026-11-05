#include <stdint.h>
#include <stdlib.h>
typedef int (*protect_fn)(uintptr_t, int);
typedef int (*filter_fn)(uintptr_t, const char*);
typedef void (*record_fn)(uintptr_t, const char*, int);
static inline int call_protect(uintptr_t fn, uintptr_t ctx, int fd) { return ((protect_fn)fn)(ctx, fd); }
static inline int call_filter(uintptr_t fn, uintptr_t ctx, const char* host) { return ((filter_fn)fn)(ctx, host); }
static inline void call_record(uintptr_t fn, uintptr_t ctx, const char* host, int outcome) { ((record_fn)fn)(ctx, host, outcome); }
