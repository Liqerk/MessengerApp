#ifndef UTILS_H
#define UTILS_H

#include "byedpi/params.h"

extern struct params default_params;

void reset_params(void);
int parse_args(int argc, char **argv);

#endif